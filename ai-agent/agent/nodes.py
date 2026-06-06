"""
LangGraph Nodes — SilverTongue AI Agent
=========================================
Nodes:
    1. transcribe_audio  — STT 转写用户音频（Kafka 管线或同步 fallback）
    2. retrieve_context   — M10 RAG 向量检索
    3. analyze_chinglish   — M11 中式英语检测
    4. generate_reply      — M9 模型路由 → LLM 生成回复
    5. synthesize_audio    — TTS 合成（仅 text_only 模型时触发）
"""

import os
import uuid
from typing import Dict, Any, List
from agent.state import AgentState
from services.embedding import VectorDBClient
from services.chinglish_detector import ChinglishDetector
from loguru import logger

# ---------------------------------------------------------------------------
# Shared singletons (initialised once at import time)
# ---------------------------------------------------------------------------
vector_db = VectorDBClient()
chinglish_detector = ChinglishDetector()

# These will be injected by main.py via configure_nodes()
_model_router = None
_stt_service = None
_tts_service = None
_conversation_store = None


def configure_nodes(model_router, stt_service, tts_service, conversation_store):
    """Inject runtime dependencies. Called once at startup from main.py."""
    global _model_router, _stt_service, _tts_service, _conversation_store
    _model_router = model_router
    _stt_service = stt_service
    _tts_service = tts_service
    _conversation_store = conversation_store
    logger.info("LangGraph nodes configured with router / STT / TTS / ES store")


# ===========================
# Node: transcribe_audio
# ===========================

def transcribe_audio(state: AgentState) -> dict:
    """
    Transcribe user audio to text using the STT service.
    Even if the model supports native voice, we ALWAYS transcribe for:
        1. Frontend text display
        2. ES full-text persistence
    """
    logger.info(f"Transcribing audio for session {state['session_id']}")

    audio_buffer = state.get("current_audio_buffer", b"")
    if audio_buffer and _stt_service:
        transcript = _stt_service.transcribe(audio_buffer)
    else:
        # Fallback: use existing text from messages
        last_user = next(
            (m for m in reversed(state.get("messages", [])) if m["role"] == "user"),
            None,
        )
        transcript = last_user["content"] if last_user else ""

    logger.info(f"Transcript: {transcript[:100]}...")

    # Persist user message to ES
    if _conversation_store and transcript:
        _conversation_store.persist_message(
            msg_id=str(uuid.uuid4()),
            session_id=state.get("session_id", ""),
            user_id=state.get("user_id", ""),
            role="user",
            content=transcript,
        )

    # Update messages with the transcribed text
    return {
        "user_transcript": transcript,
        "messages": [{"role": "user", "content": transcript, "audio_url": None}],
        "next_node": "retrieve_context",
    }


# ===========================
# Node: retrieve_context (M10)
# ===========================

def retrieve_context(state: AgentState) -> dict:
    """Retrieve relevant context from Milvus for RAG."""
    logger.info(f"Retrieving context for session {state['session_id']}")

    query_text = state.get("user_transcript", "")
    if not query_text:
        last_user = next(
            (m for m in reversed(state.get("messages", [])) if m["role"] == "user"),
            None,
        )
        query_text = last_user["content"] if last_user else ""

    rag_context = ""
    if query_text and vector_db.collection:
        retrieved = vector_db.search(state.get("user_id", ""), query_text)
        logger.info(f"Retrieved {len(retrieved)} context chunks from Milvus")
        if retrieved:
            rag_context = "\n".join(
                f"- {chunk['text']}" for chunk in retrieved if chunk.get("text")
            )

    return {"next_node": "analyze_chinglish", "refined_text": rag_context or None}


# ===========================
# Node: analyze_chinglish (M11)
# ===========================

def analyze_chinglish(state: AgentState) -> dict:
    """Detect Chinglish patterns in the user's input."""
    logger.info(f"Analyzing Chinglish for session {state['session_id']}")

    query_text = state.get("user_transcript", "")
    if not query_text:
        last_user = next(
            (m for m in reversed(state.get("messages", [])) if m["role"] == "user"),
            None,
        )
        query_text = last_user["content"] if last_user else ""

    if query_text:
        analysis = chinglish_detector.detect(query_text)
        logger.info(f"Chinglish analysis: {analysis['has_chinglish']}")
        return {"chinglish_analysis": analysis, "next_node": "generate_reply"}

    return {"next_node": "generate_reply"}


# ===========================
# Node: generate_reply (M9)
# ===========================

import json

# Load SYSTEM_PROMPT from text file
_PROMPT_FILE_PATH = os.path.join(os.path.dirname(__file__), "system_prompt.txt")
try:
    with open(_PROMPT_FILE_PATH, "r", encoding="utf-8") as f:
        SYSTEM_PROMPT = f.read()
except Exception as e:
    logger.error(f"Failed to load system_prompt.txt: {e}")
    SYSTEM_PROMPT = "You are SilverTongue AI. Your role/scenario is: {topic}. {rag_section} {chinglish_section}"

# Load detailed role descriptions
_ROLES_FILE_PATH = os.path.join(os.path.dirname(__file__), "roles.json")
try:
    with open(_ROLES_FILE_PATH, "r", encoding="utf-8") as f:
        PRESET_ROLES = json.load(f)
except Exception as e:
    logger.error(f"Failed to load roles.json: {e}")
    PRESET_ROLES = {}


def _build_system_prompt(state: AgentState) -> str:
    user_level = state.get("user_level", "intermediate")
    topic = state.get("topic", "free talk")
    
    # Map topic to detailed role description if it's a preset role
    detailed_topic = PRESET_ROLES.get(topic, topic)

    rag_text = state.get("refined_text") or ""
    rag_section = ""
    if rag_text:
        rag_section = f"\nRelevant learning materials:\n{rag_text}"

    chinglish = state.get("chinglish_analysis")
    chinglish_section = ""
    if chinglish and chinglish.get("has_chinglish"):
        patterns = chinglish.get("patterns", [])
        corrections = "; ".join(
            f'"{p["error_text"]}" → "{p["suggestion"]}" ({p["severity"]})'
            for p in patterns
        )
        chinglish_section = f"\nChinglish detected: {corrections}\nGently address these."

    system_content = SYSTEM_PROMPT.format(
        user_level=user_level, topic=detailed_topic,
        rag_section=rag_section, chinglish_section=chinglish_section,
    )

    context_text = state.get("context_text")
    if context_text:
        system_content += f"\n\nHere is the background document provided by the user (e.g. resume, menu):\n<Document>\n{context_text}\n</Document>\n"

    return system_content


def _build_chat_messages(state: AgentState) -> List[Dict[str, str]]:
    system_prompt = _build_system_prompt(state)
    openai_messages = [{"role": "system", "content": system_prompt}]
    for msg in state.get("messages", []):
        role = msg["role"]
        if role == "agent":
            role = "assistant"
        openai_messages.append({"role": role, "content": msg["content"]})
    return openai_messages


def generate_reply(state: AgentState) -> dict:
    """
    Generate AI reply via the ModelRouter.
    Selects the best available provider based on capability and priority.
    """
    logger.info(f"Generating reply for session {state['session_id']}")

    if _model_router is None:
        return {
            "messages": [{"role": "agent", "content": "[ERROR] Model router not configured", "audio_url": None}],
            "selected_provider": "none",
            "model_capability": "text_only",
            "response_audio": b"",
            "next_node": "end",
        }

    # Select provider — prefer voice if audio data is available
    has_audio = bool(state.get("current_audio_buffer", b""))
    provider = _model_router.select(prefer_voice=has_audio)

    if provider is None:
        return {
            "messages": [{"role": "agent", "content": "[ERROR] No model provider available", "audio_url": None}],
            "selected_provider": "none",
            "model_capability": "text_only",
            "response_audio": b"",
            "next_node": "end",
        }

    logger.info(f"Selected provider: {provider.name} ({provider.capability.value})")

    messages = _build_chat_messages(state)
    audio_input = state.get("current_audio_buffer") if provider.supports_voice else None

    try:
        response = provider.chat(messages, audio_data=audio_input)
        reply_text = response.text
        reply_audio = response.audio_data  # Non-empty only for VOICE_FULL
    except Exception as e:
        logger.error(f"Model call failed: {e}")
        reply_text = f"Sorry, I encountered an error. Could you try again?"
        reply_audio = b""

    # Persist AI reply to ES
    if _conversation_store and reply_text:
        refined = state.get("refined_text", "")
        _conversation_store.persist_message(
            msg_id=str(uuid.uuid4()),
            session_id=state.get("session_id", ""),
            user_id=state.get("user_id", ""),
            role="assistant",
            content=reply_text,
            refined_content=refined,
        )

    new_message = {"role": "agent", "content": reply_text, "audio_url": None}

    return {
        "messages": [new_message],
        "selected_provider": provider.name,
        "model_capability": provider.capability.value,
        "response_audio": reply_audio,
        "next_node": "end",
    }


# ===========================
# Node: synthesize_audio
# ===========================

def synthesize_audio(state: AgentState) -> dict:
    """
    TTS synthesis — only runs when the model returned text without audio.
    Skipped entirely if the provider is voice_full.
    """
    # If provider already returned audio, skip
    if state.get("response_audio", b""):
        logger.info("synthesize_audio: provider already returned audio, skipping TTS")
        return {"next_node": "end"}

    if _tts_service is None:
        logger.warning("synthesize_audio: TTS service not available")
        return {"next_node": "end"}

    last_agent = next(
        (m for m in reversed(state.get("messages", [])) if m["role"] == "agent"),
        None,
    )
    if not last_agent:
        return {"next_node": "end"}

    text = last_agent["content"]
    logger.info(f"Synthesizing audio for reply ({len(text)} chars)")

    try:
        audio_bytes = _tts_service.synthesize(text)
        return {"response_audio": audio_bytes, "next_node": "end"}
    except Exception as e:
        logger.error(f"TTS synthesis failed: {e}")
        return {"next_node": "end"}

import os
from typing import Dict, Any, List
from agent.state import AgentState
from services.embedding import VectorDBClient
from services.chinglish_detector import ChinglishDetector
from loguru import logger

# Initialize clients
vector_db = VectorDBClient()
chinglish_detector = ChinglishDetector()

# ---------------------------------------------------------------------------
# LLM configuration – supports OpenAI-compatible APIs (e.g. Qwen2.5-Omni)
# ---------------------------------------------------------------------------
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.openai.com/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "qwen2.5-omni")

_openai_client = None

def _get_llm_client():
    """Lazily initialise the OpenAI-compatible client."""
    global _openai_client
    if _openai_client is not None:
        return _openai_client
    if not LLM_API_KEY:
        logger.warning("LLM_API_KEY not set – generate_reply will use fallback mock")
        return None
    try:
        from openai import OpenAI
        _openai_client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL)
        logger.info(f"OpenAI client initialised (base_url={LLM_BASE_URL}, model={LLM_MODEL})")
        return _openai_client
    except ImportError:
        logger.warning("openai package not installed – using fallback mock")
        return None
    except Exception as e:
        logger.error(f"Failed to create OpenAI client: {e}")
        return None


# ===========================
# Node: retrieve_context (M10)
# ===========================

def retrieve_context(state: AgentState) -> dict:
    """
    Node: Retrieve context from Milvus (M10 RAG)
    """
    logger.info(f"Retrieving context for session {state['session_id']}")

    last_user_msg = next(
        (m for m in reversed(state.get('messages', [])) if m['role'] == 'user'),
        None,
    )

    rag_context = ""
    if last_user_msg and vector_db.collection:
        retrieved = vector_db.search(state.get('user_id', ''), last_user_msg['content'])
        logger.info(f"Retrieved {len(retrieved)} context chunks from Milvus")
        if retrieved:
            rag_context = "\n".join(
                f"- {chunk['text']}" for chunk in retrieved if chunk.get('text')
            )

    return {"next_node": "analyze_chinglish", "refined_text": rag_context or None}


# ===========================
# Node: analyze_chinglish (M11)
# ===========================

def analyze_chinglish(state: AgentState) -> dict:
    """
    Node: Analyze user input for Chinglish patterns (M11)
    """
    logger.info(f"Analyzing Chinglish for session {state['session_id']}")

    last_user_msg = next(
        (m for m in reversed(state.get('messages', [])) if m['role'] == 'user'),
        None,
    )

    if last_user_msg:
        analysis = chinglish_detector.detect(last_user_msg['content'])
        logger.info(f"Chinglish analysis: {analysis['has_chinglish']}")
        return {"chinglish_analysis": analysis, "next_node": "generate_reply"}

    return {"next_node": "generate_reply"}


# ===========================
# Node: generate_reply (M9)
# ===========================

SYSTEM_PROMPT = """You are SilverTongue AI, an English conversation coach.
Your role is to help Chinese learners practice spoken English through natural dialogue.

Guidelines:
- Adjust your language complexity to the learner's CEFR level ({user_level}).
- The current conversation topic is: {topic}.
- Encourage the learner by acknowledging good expressions.
- If Chinglish patterns are detected, gently correct them and explain the natural alternative.
- Keep replies concise (2-4 sentences) to maintain conversational flow.
{rag_section}
{chinglish_section}"""


def _build_system_prompt(state: AgentState) -> str:
    """Build the system prompt with RAG context and Chinglish analysis."""
    user_level = state.get("user_level", "intermediate")
    topic = state.get("topic", "free talk")

    # RAG context
    rag_text = state.get("refined_text") or ""
    rag_section = ""
    if rag_text:
        rag_section = f"\nRelevant learning materials the student has studied:\n{rag_text}\nTry to reference these materials when appropriate."

    # Chinglish analysis
    chinglish = state.get("chinglish_analysis")
    chinglish_section = ""
    if chinglish and chinglish.get("has_chinglish"):
        patterns = chinglish.get("patterns", [])
        corrections = "; ".join(
            f'"{p["error_text"]}" → "{p["suggestion"]}" ({p["severity"]})'
            for p in patterns
        )
        chinglish_section = f"\nThe learner's last message contains Chinglish: {corrections}\nPlease address these gently in your reply."

    return SYSTEM_PROMPT.format(
        user_level=user_level,
        topic=topic,
        rag_section=rag_section,
        chinglish_section=chinglish_section,
    )


def _build_chat_messages(state: AgentState) -> List[Dict[str, str]]:
    """Convert agent state messages to OpenAI chat format."""
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
    Node: Generate AI reply using LLM (OpenAI-compatible API).

    Falls back to a context-aware mock reply when LLM_API_KEY is not
    configured, so the full pipeline can be tested end-to-end.
    """
    logger.info(f"Generating reply for session {state['session_id']}")

    client = _get_llm_client()

    if client is not None:
        try:
            messages = _build_chat_messages(state)
            response = client.chat.completions.create(
                model=LLM_MODEL,
                messages=messages,
                temperature=0.7,
                max_tokens=256,
            )
            reply_content = response.choices[0].message.content
            logger.info("LLM reply generated successfully")
        except Exception as e:
            logger.error(f"LLM call failed, falling back to mock: {e}")
            reply_content = _mock_reply(state)
    else:
        reply_content = _mock_reply(state)

    new_message = {"role": "agent", "content": reply_content, "audio_url": None}

    return {
        "messages": [new_message],
        "next_node": "end",
    }


def _mock_reply(state: AgentState) -> str:
    """
    Context-aware mock reply when LLM is unavailable.
    Uses Chinglish analysis and conversation context to produce
    a meaningful (though templated) response.
    """
    last_user_msg = next(
        (m for m in reversed(state.get("messages", [])) if m["role"] == "user"),
        None,
    )
    user_text = last_user_msg["content"] if last_user_msg else ""

    chinglish = state.get("chinglish_analysis")
    if chinglish and chinglish.get("has_chinglish"):
        patterns = chinglish.get("patterns", [])
        first = patterns[0] if patterns else {}
        correction = first.get("suggestion", "")
        error = first.get("error_text", "")
        return (
            f"That's a great effort! Just a small note — instead of "
            f'"{error}", a more natural way to say it would be '
            f'"{correction}". Keep practicing, you\'re doing well!'
        )

    topic = state.get("topic", "")
    if topic:
        return (
            f"Interesting point about {topic}! "
            f"Could you tell me more about what you think? "
            f"I'd love to hear your perspective."
        )

    return (
        "That's a good point! Let me think about that. "
        "Could you elaborate a bit more on what you mean? "
        "I want to make sure I understand your perspective correctly."
    )

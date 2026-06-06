"""
AgentService gRPC Servicer
============================
Implements the AgentService defined in agent.proto.
All audio flows through the Kafka-backed STT pipeline.
"""

import grpc
from loguru import logger
import sys
import os
import json
import time
import uuid
import redis
import fitz
import docx
from minio import Minio

# Add proto to path
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'proto'))
import agent_pb2
import agent_pb2_grpc

from agent.graph import agent_graph
from services.metrics import grpc_metric, AI_INFERENCE_TTFT_LATENCY

# Lazy-initialized redis client (DB 3 for session state)
_redis_client = None

def _get_redis():
    global _redis_client
    if _redis_client is None:
        redis_url = os.getenv("REDIS_URL", "redis://localhost:6379/3")
        try:
            _redis_client = redis.Redis.from_url(redis_url, decode_responses=True)
            logger.info(f"Connected to Redis for session store at {redis_url}")
        except Exception as e:
            logger.error(f"Failed to connect to Redis: {e}")
    return _redis_client


# These will be injected from main.py
_audio_pipeline = None
_conversation_store = None

def configure_agent_service(audio_pipeline, conversation_store):
    """Inject runtime dependencies."""
    global _audio_pipeline, _conversation_store
    _audio_pipeline = audio_pipeline
    _conversation_store = conversation_store


class AgentServiceServicer(agent_pb2_grpc.AgentServiceServicer):

    @grpc_metric("AgentService")
    def StartSession(self, request, context):
        """Start session and persist settings to Redis."""
        logger.info(f"Starting session {request.session_id} for user {request.user_id}")

        r = _get_redis()
        if r:
            try:
                context_text = ""
                # Parse context file if provided
                if getattr(request, "context_file_url", None):
                    try:
                        logger.info(f"Extracting context from {request.context_file_url}")
                        minio_client = Minio(
                            os.getenv("MINIO_ENDPOINT", "localhost:9000"),
                            access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
                            secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"),
                            secure=False
                        )
                        bucket = os.getenv("MINIO_RECORDINGS_BUCKET", "st-recordings")
                        
                        response = minio_client.get_object(bucket, request.context_file_url)
                        file_data = response.read()
                        response.close()
                        response.release_conn()
                        
                        ext = request.context_file_url.split('.')[-1].lower() if '.' in request.context_file_url else ""
                        if ext == "pdf":
                            doc = fitz.open(stream=file_data, filetype="pdf")
                            context_text = "\n".join(page.get_text() for page in doc)
                            doc.close()
                        elif ext in ["docx", "doc"]:
                            from io import BytesIO
                            doc = docx.Document(BytesIO(file_data))
                            context_text = "\n".join(para.text for para in doc.paragraphs)
                        else:
                            context_text = file_data.decode("utf-8", errors="ignore")
                            
                        if len(context_text) > 10000:
                            logger.warning(f"Truncating context text from {len(context_text)} to 10000 chars")
                            context_text = context_text[:10000]
                            
                    except Exception as e:
                        logger.error(f"Failed to extract context file: {e}")

                session_data = {
                    "user_id": request.user_id,
                    "user_level": request.user_level,
                    "topic": request.topic,
                    "mode": request.mode,
                    "context_text": context_text,
                    "created_at": time.time()
                }
                r.set(f"session:{request.session_id}", json.dumps(session_data), ex=86400)
            except Exception as e:
                logger.error(f"Failed to save session to Redis: {e}")
                return agent_pb2.StartSessionResponse(success=False, error_message=str(e))

        return agent_pb2.StartSessionResponse(success=True)

    @grpc_metric("AgentService")
    def ChatStream(self, request_iterator, context):
        """
        Bidirectional stream for real-time chat.
        All audio ALWAYS goes through STT (even if model supports voice),
        because text must be displayed on frontend and persisted in ES.
        """
        r = _get_redis()
        audio_buffer = bytearray()
        session_id = ""

        for request in request_iterator:
            session_id = request.session_id
            audio_buffer.extend(request.audio_chunk)

            if not request.is_final_chunk:
                continue

            # ---- Final chunk received — process the complete utterance ----
            start_inference = time.time()

            # Fetch session metadata
            user_id = "unknown"
            user_level = "intermediate"
            topic = "free talk"

            if r:
                try:
                    raw = r.get(f"session:{session_id}")
                    if raw:
                        data = json.loads(raw)
                        user_id = data.get("user_id", "unknown")
                        user_level = data.get("user_level", "intermediate")
                        topic = data.get("topic", "free talk")
                        context_text = data.get("context_text", None)
                except Exception as e:
                    logger.error(f"Error loading session metadata: {e}")

            # Produce audio to Kafka for async STT (ES persistence handled there)
            if _audio_pipeline and bytes(audio_buffer):
                _audio_pipeline.produce(bytes(audio_buffer), session_id, user_id)

            # Run the full LangGraph pipeline
            # (transcribe_audio node will also STT synchronously for immediate reply)
            state = {
                "session_id": session_id,
                "user_id": user_id,
                "user_level": user_level,
                "topic": topic,
                "context_text": context_text if 'context_text' in locals() else None,
                "mode": "full_duplex",
                "current_audio_buffer": bytes(audio_buffer),
                "is_user_speaking": False,
                "turn_taken": True,
                "messages": [],
                "chinglish_analysis": {},
                "refined_text": None,
                "user_transcript": "",
                "response_audio": b"",
                "selected_provider": "",
                "model_capability": "text_only",
                "next_node": "",
            }

            try:
                result_state = agent_graph.invoke(state)

                # Get the last AI message
                agent_msg = next(
                    (m for m in reversed(result_state.get("messages", [])) if m["role"] == "agent"),
                    None,
                )
                reply_text = agent_msg["content"] if agent_msg else "I couldn't generate a reply."
                reply_audio = result_state.get("response_audio", b"")
                model_cap = result_state.get("model_capability", "text_only")

                # Chinglish analysis
                chinglish_data = result_state.get("chinglish_analysis")
                chinglish_pb = None
                if chinglish_data and chinglish_data.get("has_chinglish"):
                    patterns = chinglish_data.get("patterns", [])
                    first = patterns[0] if patterns else {}
                    chinglish_pb = agent_pb2.ChinglishAnalysis(
                        has_chinglish=True,
                        original_pattern=first.get("error_text", ""),
                        suggestion=first.get("suggestion", ""),
                        severity=first.get("severity", "medium"),
                    )

                refined = result_state.get("refined_text", "") or ""

                # Record TTFT latency
                ttft = time.time() - start_inference
                provider_name = result_state.get("selected_provider", "unknown")
                AI_INFERENCE_TTFT_LATENCY.labels(model_name=provider_name).observe(ttft)

                # Stream the reply text word-by-word
                words = reply_text.split(" ")
                for i, w in enumerate(words):
                    is_last = (i == len(words) - 1)
                    delta = w if is_last else w + " "

                    yield agent_pb2.ChatStreamResponse(
                        text_delta=delta,
                        audio_chunk=reply_audio if is_last else b"",
                        is_finished=is_last,
                        chinglish=chinglish_pb if is_last else None,
                        refined_text=refined if is_last else "",
                    )
                    time.sleep(0.03)

            except Exception as e:
                logger.error(f"Error running LangGraph: {e}")
                context.abort(grpc.StatusCode.INTERNAL, str(e))

            # Reset buffer for the next utterance
            audio_buffer = bytearray()

    @grpc_metric("AgentService")
    def GetScaffolding(self, request, context):
        """
        M15: Guided completion (Scaffolding Engine).
        Generates contextual hints based on user's CEFR level and session topic.
        """
        logger.info(f"Generating scaffolding for session {request.session_id}")

        r = _get_redis()
        user_level = "B1"
        if r:
            try:
                raw = r.get(f"session:{request.session_id}")
                if raw:
                    data = json.loads(raw)
                    user_level = data.get("user_level", "B1")
            except Exception as e:
                logger.error(f"Failed to fetch session level: {e}")

        level = user_level.upper()
        prefix = request.incomplete_text

        if level in ("A1", "A2"):
            hints = [
                f"{prefix} and I like it.",
                f"{prefix} because it is fun.",
                f"{prefix} with my friends.",
            ]
        elif level in ("B1", "B2"):
            hints = [
                f"{prefix}, which provides a great opportunity to learn.",
                f"{prefix} from my own personal perspective.",
                f"{prefix}, although there are some challenges involved.",
            ]
        else:
            hints = [
                f"{prefix}, thereby facilitating a deeper understanding of the subject matter.",
                f"{prefix}, which is fundamentally critical to achieving long-term efficacy.",
                f"{prefix}, conversely leading to several unintended consequences.",
            ]

        return agent_pb2.ScaffoldingResponse(completion_hints=hints)

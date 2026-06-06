import grpc
from loguru import logger
import sys
import os
import json
import time
import redis

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


class AgentServiceServicer(agent_pb2_grpc.AgentServiceServicer):

    @grpc_metric("AgentService")
    def StartSession(self, request, context):
        """
        Start session and persist settings (user_id, level, topic, mode) to Redis.
        """
        logger.info(f"Starting session {request.session_id} for user {request.user_id}")
        
        r = _get_redis()
        if r:
            try:
                session_data = {
                    "user_id": request.user_id,
                    "user_level": request.user_level,
                    "topic": request.topic,
                    "mode": request.mode,
                    "created_at": time.time()
                }
                r.set(f"session:{request.session_id}", json.dumps(session_data), ex=86400) # Expire in 24h
            except Exception as e:
                logger.error(f"Failed to save session settings to Redis: {e}")
                return agent_pb2.StartSessionResponse(success=False, error_message=str(e))
                
        return agent_pb2.StartSessionResponse(success=True)

    @grpc_metric("AgentService")
    def ChatStream(self, request_iterator, context):
        """
        Bidirectional stream for real-time chat.
        Accumulates audio chunks, transcribes them when final, runs the agent graph,
        and streams back text chunks.
        """
        r = _get_redis()
        
        for request in request_iterator:
            logger.info(f"Received audio chunk for session {request.session_id}")
            
            # Here we accumulate audio bytes.
            # In a production app, we would write audio to a temp file and run ASR.
            # For our simulation, we mock the transcription when is_final_chunk is true.
            if not request.is_final_chunk:
                continue
                
            start_inference = time.time()
            
            # Fetch session metadata from Redis
            user_id = "unknown"
            user_level = "intermediate"
            topic = "free talk"
            
            if r:
                try:
                    raw_data = r.get(f"session:{request.session_id}")
                    if raw_data:
                        data = json.loads(raw_data)
                        user_id = data.get("user_id", "unknown")
                        user_level = data.get("user_level", "intermediate")
                        topic = data.get("topic", "free talk")
                except Exception as e:
                    logger.error(f"Error loading session metadata: {e}")
            
            # Deterministic mock user transcript:
            # We use a Chinglish sentence on the first turn to demonstrate error correction!
            user_transcript = "I very like English and want to improve my spoken skills."
            
            # Run LangGraph with the transcribed text
            state = {
                "session_id": request.session_id,
                "user_id": user_id,
                "user_level": user_level,
                "topic": topic,
                "messages": [{"role": "user", "content": user_transcript, "audio_url": None}]
            }
            
            try:
                result_state = agent_graph.invoke(state)
                
                # Get the last AI message
                agent_msg = next((m for m in reversed(result_state.get('messages', [])) if m['role'] == 'agent'), None)
                reply_text = agent_msg['content'] if agent_msg else "I couldn't generate a reply."
                
                # Get chinglish analysis
                chinglish_data = result_state.get('chinglish_analysis', None)
                chinglish_pb = None
                
                if chinglish_data and chinglish_data.get("has_chinglish"):
                    patterns = chinglish_data.get("patterns", [])
                    first_pattern = patterns[0] if patterns else {}
                    chinglish_pb = agent_pb2.ChinglishAnalysis(
                        has_chinglish=True,
                        original_pattern=first_pattern.get("error_text", ""),
                        suggestion=first_pattern.get("suggestion", ""),
                        severity=first_pattern.get("severity", "medium")
                    )
                
                refined_text = result_state.get("refined_text", "")
                
                # Record TTFT latency
                ttft_duration = time.time() - start_inference
                AI_INFERENCE_TTFT_LATENCY.labels(model_name="qwen-2.5-omni").observe(ttft_duration)
                
                # Simulate streaming of the reply text
                words = reply_text.split(" ")
                for i, w in enumerate(words):
                    is_last = (i == len(words) - 1)
                    # Add space back
                    delta = w if is_last else w + " "
                    
                    yield agent_pb2.ChatStreamResponse(
                        text_delta=delta,
                        audio_chunk=b"",
                        is_finished=is_last,
                        chinglish=chinglish_pb if is_last else None,
                        refined_text=refined_text if is_last else ""
                    )
                    time.sleep(0.05) # simulate network/generation streaming delay
                    
            except Exception as e:
                logger.error(f"Error running LangGraph: {e}")
                context.abort(grpc.StatusCode.INTERNAL, str(e))

    @grpc_metric("AgentService")
    def GetScaffolding(self, request, context):
        """
        M15: Guided completion (Scaffolding Engine)
        Generates contextual hints based on user's CEFR level and session topic.
        """
        logger.info(f"Generating scaffolding for session {request.session_id}, incomplete: '{request.incomplete_text}'")
        
        r = _get_redis()
        user_level = "B1"
        if r:
            try:
                raw_data = r.get(f"session:{request.session_id}")
                if raw_data:
                    data = json.loads(raw_data)
                    user_level = data.get("user_level", "B1")
            except Exception as e:
                logger.error(f"Failed to fetch session level: {e}")
                
        level = user_level.upper()
        suggestions = []
        
        # Rule-based suggestions based on CEFR level
        if level in ["A1", "A2"]:
            suggestions = [
                f"{request.incomplete_text} and I like it.",
                f"{request.incomplete_text} because it is fun.",
                f"{request.incomplete_text} with my friends."
            ]
        elif level in ["B1", "B2"]:
            suggestions = [
                f"{request.incomplete_text}, which provides a great opportunity to learn.",
                f"{request.incomplete_text} from my own personal perspective.",
                f"{request.incomplete_text}, although there are some challenges involved."
            ]
        else: # C1, C2
            suggestions = [
                f"{request.incomplete_text}, thereby facilitating a deeper understanding of the subject matter.",
                f"{request.incomplete_text}, which is fundamentally critical to achieving long-term efficacy.",
                f"{request.incomplete_text}, conversely leading to several unintended consequences."
            ]
            
        return agent_pb2.ScaffoldingResponse(
            completion_hints=suggestions
        )

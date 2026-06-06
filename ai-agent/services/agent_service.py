import grpc
from loguru import logger
import sys
import os
import json
import time

# Add proto to path
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'proto'))
import agent_pb2
import agent_pb2_grpc

from agent.graph import agent_graph
from services.metrics import grpc_metric, AI_INFERENCE_TTFT_LATENCY

class AgentServiceServicer(agent_pb2_grpc.AgentServiceServicer):
    @grpc_metric("AgentService")
    def StartSession(self, request, context):
        logger.info(f"Starting session {request.session_id} for user {request.user_id}")
        
        # Here we would initialize the session state in Redis
        
        return agent_pb2.SessionResponse(
            session_id=request.session_id,
            status="STARTED",
            initial_message="Hello, I am SilverTongue AI. How can I help you practice today?"
        )
        
    @grpc_metric("AgentService")
    def ChatStream(self, request_iterator, context):
        """
        Bidirectional stream for real-time chat.
        """
        for request in request_iterator:
            logger.info(f"Received message for session {request.session_id}: {request.text_content}")
            
            start_inference = time.time()
            
            # Prepare state
            state = {
                "session_id": request.session_id,
                "user_id": request.session_id.split('_')[0] if '_' in request.session_id else "unknown",
                "messages": [{"role": "user", "content": request.text_content, "audio_url": None}]
            }
            
            # Run LangGraph
            try:
                result_state = agent_graph.invoke(state)
                
                # Get the last AI message
                agent_msg = next((m for m in reversed(result_state.get('messages', [])) if m['role'] == 'agent'), None)
                reply_text = agent_msg['content'] if agent_msg else "I couldn't generate a reply."
                
                # Get chinglish analysis
                chinglish = result_state.get('chinglish_analysis', None)
                chinglish_json = json.dumps(chinglish) if chinglish else ""
                
                # Record TTFT latency
                ttft_duration = time.time() - start_inference
                AI_INFERENCE_TTFT_LATENCY.labels(model_name="qwen-2.5-omni").observe(ttft_duration)
                
                yield agent_pb2.ChatStreamResponse(
                    session_id=request.session_id,
                    text_content=reply_text,
                    is_final=True,
                    chinglish_analysis=chinglish_json
                )
                
            except Exception as e:
                logger.error(f"Error running LangGraph: {e}")
                context.abort(grpc.StatusCode.INTERNAL, str(e))
                
    @grpc_metric("AgentService")
    def GetScaffolding(self, request, context):
        """
        M15: Guided completion (Scaffolding Engine)
        Generates contextual hints based on user's CEFR level.
        """
        logger.info(f"Generating scaffolding for user {request.user_id} with CEFR level: {request.user_level}")
        
        level = request.user_level.upper()
        suggestions = []
        
        # Mock logic based on CEFR level
        if level in ["A1", "A2"]:
            suggestions = [
                "I think we should...",
                "Could you please repeat that?",
                "I agree with you."
            ]
        elif level in ["B1", "B2"]:
            suggestions = [
                "From my perspective, this means...",
                "I'm not entirely sure, but I guess...",
                "That's an interesting point, furthermore..."
            ]
        else: # C1, C2
            suggestions = [
                "To elaborate on that specific aspect...",
                "Conversely, one might argue that...",
                "Given the current circumstances, it's evident that..."
            ]
            
        return agent_pb2.ScaffoldingResponse(
            suggestions=suggestions
        )


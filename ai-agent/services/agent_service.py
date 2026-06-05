import grpc
from loguru import logger
import sys
import os
import json

# Add proto to path
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'proto'))
import agent_pb2
import agent_pb2_grpc

from agent.graph import agent_graph

class AgentServiceServicer(agent_pb2_grpc.AgentServiceServicer):
    def StartSession(self, request, context):
        logger.info(f"Starting session {request.session_id} for user {request.user_id}")
        
        # Here we would initialize the session state in Redis
        
        return agent_pb2.SessionResponse(
            session_id=request.session_id,
            status="STARTED",
            initial_message="Hello, I am SilverTongue AI. How can I help you practice today?"
        )
        
    def ChatStream(self, request_iterator, context):
        """
        Bidirectional stream for real-time chat.
        """
        for request in request_iterator:
            logger.info(f"Received message for session {request.session_id}: {request.text_content}")
            
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
                
                yield agent_pb2.ChatStreamResponse(
                    session_id=request.session_id,
                    text_content=reply_text,
                    is_final=True,
                    chinglish_analysis=chinglish_json
                )
                
            except Exception as e:
                logger.error(f"Error running LangGraph: {e}")
                context.abort(grpc.StatusCode.INTERNAL, str(e))
                
    def GetScaffolding(self, request, context):
        """
        M15: Guided completion (placeholder for now)
        """
        return agent_pb2.ScaffoldingResponse(
            suggestions=["Could you repeat that?", "I think we should..."]
        )

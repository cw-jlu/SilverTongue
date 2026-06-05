from typing import Dict, Any
from agent.state import AgentState
from services.embedding import VectorDBClient
from services.chinglish_detector import ChinglishDetector
from loguru import logger

# Initialize clients
vector_db = VectorDBClient()
chinglish_detector = ChinglishDetector()

def retrieve_context(state: AgentState) -> dict:
    """
    Node: Retrieve context from Milvus (M10 RAG)
    """
    logger.info(f"Retrieving context for session {state['session_id']}")
    
    # Get last user message
    last_user_msg = next((m for m in reversed(state.get('messages', [])) if m['role'] == 'user'), None)
    
    if last_user_msg and vector_db.collection:
        retrieved = vector_db.search(state['user_id'], last_user_msg['content'])
        logger.info(f"Retrieved {len(retrieved)} context chunks from Milvus")
        # In a real app, we'd inject this context into a system prompt
        
    return {"next_node": "analyze_chinglish"}

def analyze_chinglish(state: AgentState) -> dict:
    """
    Node: Analyze user input for Chinglish patterns (M11)
    """
    logger.info(f"Analyzing Chinglish for session {state['session_id']}")
    
    last_user_msg = next((m for m in reversed(state.get('messages', [])) if m['role'] == 'user'), None)
    
    if last_user_msg:
        analysis = chinglish_detector.detect(last_user_msg['content'])
        logger.info(f"Chinglish analysis: {analysis['has_chinglish']}")
        return {"chinglish_analysis": analysis, "next_node": "generate_reply"}
        
    return {"next_node": "generate_reply"}

def generate_reply(state: AgentState) -> dict:
    """
    Node: Generate AI reply using LLM (Mocked for now)
    """
    logger.info(f"Generating reply for session {state['session_id']}")
    
    # Here we would call langchain-openai with Qwen2.5-Omni
    # passing state['messages'] and context from RAG
    
    reply_content = "This is a mocked reply from the AI Agent based on context and state."
    
    new_message = {"role": "agent", "content": reply_content, "audio_url": None}
    
    return {
        "messages": [new_message],
        "next_node": "end"
    }

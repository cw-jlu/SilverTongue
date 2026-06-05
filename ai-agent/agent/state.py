from typing import Annotated, TypedDict, List, Dict, Any, Optional

# ==========================================
# LangGraph State Model Definition
# ==========================================

class Message(TypedDict):
    role: str
    content: str
    audio_url: Optional[str]

def merge_messages(old: List[Message], new: List[Message]) -> List[Message]:
    """Append new messages to the existing list."""
    return old + new

class AgentState(TypedDict):
    """
    SilverTongue AI Agent State
    Represents the state of a single conversation session.
    """
    session_id: str
    user_id: str
    
    # Session metadata
    mode: str           # full_duplex, half_duplex, guided, free_talk
    user_level: str     # CEFR level (beginner, advanced, etc.)
    topic: str          # Conversation topic/scenario
    
    # Conversation History
    messages: Annotated[List[Message], merge_messages]
    
    # Real-time state
    current_audio_buffer: bytes
    is_user_speaking: bool
    turn_taken: bool
    
    # Analysis results
    chinglish_analysis: Dict[str, Any]
    refined_text: Optional[str]
    
    # Next step routing
    next_node: str

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
    context_text: Optional[str] # Parsed context text from uploaded files (resume, menu, etc)
    
    # Conversation History
    messages: Annotated[List[Message], merge_messages]
    
    # Real-time state
    current_audio_buffer: bytes
    is_user_speaking: bool
    turn_taken: bool
    
    # Analysis results
    chinglish_analysis: Dict[str, Any]
    refined_text: Optional[str]
    
    # Audio pipeline state (new)
    user_transcript: str            # STT 转写出的用户文本
    response_audio: bytes           # AI 回复音频（TTS 生成或模型原生返回）
    selected_provider: str          # 路由选中的模型名称
    model_capability: str           # text_only / voice_input / voice_full
    
    # Next step routing
    next_node: str

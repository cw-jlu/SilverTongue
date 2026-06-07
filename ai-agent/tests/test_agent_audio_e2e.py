import os
import sys
import wave
import struct
import math
from loguru import logger

# Ensure the parent directory is in the path
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))

# Set up environment variables to ensure ModelRouter uses DeepSeek
os.environ["DEEPSEEK_API_KEY"] = "sk-46a31dc5807046e0bdef7d369365d724"
os.environ["DEEPSEEK_MODEL"] = "deepseek-chat"

from agent.graph import create_agent_graph
from agent.nodes import configure_nodes
from services.model_router import ModelRouter
from services.stt_service import STTService
from services.tts_service import TTSService
from services.conversation_store import ConversationStore

def generate_dummy_wav(filename="dummy_input.wav"):
    """Generate a 1-second silent WAV file for testing."""
    sample_rate = 16000
    duration = 1.0
    with wave.open(filename, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        data = struct.pack('<h', 0) * int(sample_rate * duration)
        wav_file.writeframes(data)
    return filename

def test_full_audio_pipeline():
    logger.info("Initializing services for E2E graph test...")
    router = ModelRouter()
    stt = STTService()
    tts = TTSService()
    store = ConversationStore()
    
    logger.info("Configuring LangGraph nodes with real services...")
    configure_nodes(router, stt, tts, store)
    
    logger.info("Creating graph...")
    app = create_agent_graph()
    
    # Generate mock audio buffer
    wav_path = generate_dummy_wav()
    try:
        with open(wav_path, "rb") as f:
            audio_buffer = f.read()
            
        logger.info("Preparing initial state with user audio buffer...")
        initial_state = {
            "session_id": "test_e2e_session_999",
            "user_id": "user_tester",
            "mode": "free_talk",
            "user_level": "intermediate",
            "topic": "Daily Greeting",
            "context_text": "Introduce yourself.",
            "messages": [],
            "current_audio_buffer": audio_buffer,
            "is_user_speaking": False,
            "turn_taken": True,
            "chinglish_analysis": {},
            "refined_text": None,
            "user_transcript": "",
            "response_audio": b"",
        }
        
        logger.info("Invoking LangGraph with audio input...")
        result_state = app.invoke(initial_state)
        
        logger.info("=" * 60)
        logger.info("E2E GRAPH TEST COMPLETE")
        logger.info("=" * 60)
        
        user_transcript = result_state.get("user_transcript", "")
        response_audio = result_state.get("response_audio", b"")
        messages = result_state.get("messages", [])
        
        # Verify STT executed
        logger.info(f"User transcript: '{user_transcript}'")
        assert isinstance(user_transcript, str), "Should have transcribed to string"
        
        # Verify LLM replied
        if messages:
            ai_reply = messages[-1].get("content", "")
            logger.info(f"AI reply: '{ai_reply}'")
            assert len(ai_reply) > 0, "AI reply should not be empty"
        else:
            raise AssertionError("No messages in state")
            
        # Verify TTS executed
        logger.info(f"Response audio size: {len(response_audio)} bytes")
        assert len(response_audio) > 0, "Should have synthesized response audio"
        
        logger.info("🎉 E2E Audio Pipeline successfully verified and fully connected!")
        
    finally:
        if os.path.exists(wav_path):
            os.remove(wav_path)

if __name__ == "__main__":
    test_full_audio_pipeline()

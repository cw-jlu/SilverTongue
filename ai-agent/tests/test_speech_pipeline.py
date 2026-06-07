import os
import sys
import wave
import struct
import math
from loguru import logger

# Ensure parent directory is in path
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))

from services.stt_service import STTService
from services.tts_service import TTSService

def generate_dummy_wav(filename="dummy.wav"):
    """Generate a 1-second silent WAV file for API testing."""
    sample_rate = 16000
    duration = 1.0
    with wave.open(filename, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        # 1 second of silence
        data = struct.pack('<h', 0) * int(sample_rate * duration)
        wav_file.writeframes(data)
    return filename

def test_speech_pipeline():
    logger.info("Initializing services...")
    stt = STTService()
    tts = TTSService()
    
    logger.info(f"Initialized STT with engine: {stt.engine}")
    logger.info(f"Initialized TTS with engine: {tts.engine}")
    
    # 1. Test STT.ai
    wav_path = generate_dummy_wav("temp_test.wav")
    try:
        with open(wav_path, "rb") as f:
            audio_bytes = f.read()
            
        logger.info("Sending dummy audio to STT.ai...")
        transcript = stt.transcribe(audio_bytes)
        logger.info(f"STT Transcript Result: '{transcript}'")
        
        # Verify it does not crash and handles the response
        assert isinstance(transcript, str), "Transcript should be a string"
    finally:
        if os.path.exists(wav_path):
            os.remove(wav_path)
            
    # 2. Test TTS.ai
    test_text = "Hello, this is a test of the Kokoro voice model."
    logger.info(f"Sending text to TTS.ai: '{test_text}'")
    audio_output = tts.synthesize(test_text)
    
    logger.info(f"TTS Output size: {len(audio_output)} bytes")
    assert len(audio_output) > 0, "TTS should return audio bytes"
    
    # Save output to confirm it is valid MP3
    output_filename = "tts_test_result.mp3"
    with open(output_filename, "wb") as f:
        f.write(audio_output)
    logger.info(f"TTS output written to {output_filename}")
    
    logger.info("Speech pipeline test completed successfully!")

if __name__ == "__main__":
    test_speech_pipeline()

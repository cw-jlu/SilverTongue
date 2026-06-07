"""
Text-to-Speech Service
========================
Dual-engine TTS with configurable backend:
    1. ``edge-tts`` (default, free, no GPU required)
    2. Remote TTS API (CosyVoice / other, via env config)

Only invoked when the selected model does NOT return audio natively.
"""

import asyncio
import io
import os
from typing import Optional
from loguru import logger


class TTSService:
    """
    Synthesise text → audio bytes.

    Usage::

        tts = TTSService()
        audio_bytes = tts.synthesize("Hello, how are you?", voice="en-US-AriaNeural")
    """

    def __init__(self):
        self.engine: str = "none"
        self._api_client = None

        if self._init_tts_ai():
            self.engine = "tts_ai"
        elif self._init_freetts():
            self.engine = "freetts"
        elif self._init_edge_tts():
            self.engine = "edge_tts"
        elif self._init_api_tts():
            self.engine = "api"
        else:
            logger.warning("TTSService: no engine available")

        logger.info(f"TTSService initialised with engine={self.engine}")

    # ---- engine init -------------------------------------------------------

    def _init_edge_tts(self) -> bool:
        if os.getenv("TTS_FORCE_API", "").lower() == "true":
            return False
        try:
            import edge_tts  # noqa: F401
            logger.info("TTS: edge-tts available")
            return True
        except ImportError:
            logger.info("TTS: edge-tts not installed")
            return False

    def _init_api_tts(self) -> bool:
        api_key = os.getenv("TTS_API_KEY", "")
        if not api_key:
            return False
        try:
            from openai import OpenAI
            base_url = os.getenv("TTS_API_BASE_URL", "https://api.openai.com/v1")
            self._api_client = OpenAI(api_key=api_key, base_url=base_url)
            logger.info(f"TTS: using remote API at {base_url}")
            return True
        except Exception as e:
            logger.warning(f"TTS: failed to init API client: {e}")
            return False

    def _init_tts_ai(self) -> bool:
        api_key = os.getenv("TTS_AI_API_KEY", "")
        if not api_key:
            return False
        try:
            from openai import OpenAI
            base_url = os.getenv("TTS_AI_BASE_URL", "https://tts.ai/v1")
            self._tts_ai_client = OpenAI(api_key=api_key, base_url=base_url)
            logger.info(f"TTS: using tts.ai API at {base_url}")
            return True
        except Exception as e:
            logger.warning(f"TTS: failed to init tts.ai client: {e}")
            return False

    def _init_freetts(self) -> bool:
        if os.getenv("TTS_USE_FREETTS", "").lower() != "true":
            return False
        logger.info("TTS: using FreeTTS.org API")
        return True

    # ---- public API --------------------------------------------------------

    def synthesize(self, text: str, voice: Optional[str] = None) -> bytes:
        """
        Convert text to speech audio (WAV/MP3 bytes).

        Args:
            text: The text to speak.
            voice: Voice identifier. Defaults to ``en-US-AriaNeural`` for edge-tts
                   or ``alloy`` for OpenAI TTS API.

        Returns:
            Audio bytes (MP3 format). Empty bytes on failure.
        """
        if not text.strip():
            return b""

        if self.engine == "tts_ai":
            return self._synthesize_tts_ai(text, voice)
        elif self.engine == "freetts":
            return self._synthesize_freetts(text, voice)
        elif self.engine == "edge_tts":
            return self._synthesize_edge(text, voice)
        elif self.engine == "api":
            return self._synthesize_api(text, voice)
        else:
            logger.warning("TTS: no engine available")
            return b""

    # ---- engine implementations --------------------------------------------

    def _synthesize_edge(self, text: str, voice: Optional[str]) -> bytes:
        voice = voice or os.getenv("TTS_VOICE", "en-US-AriaNeural")
        try:
            import edge_tts

            async def _do():
                communicate = edge_tts.Communicate(text, voice)
                buf = io.BytesIO()
                async for chunk in communicate.stream():
                    if chunk["type"] == "audio":
                        buf.write(chunk["data"])
                return buf.getvalue()

            # Run the async edge-tts in a sync context
            try:
                loop = asyncio.get_running_loop()
            except RuntimeError:
                loop = None

            if loop and loop.is_running():
                # We're inside an async context — run in a thread
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as pool:
                    future = pool.submit(asyncio.run, _do())
                    return future.result(timeout=30)
            else:
                return asyncio.run(_do())

        except Exception as e:
            logger.error(f"TTS edge-tts error: {e}")
            return b""

    def _synthesize_api(self, text: str, voice: Optional[str]) -> bytes:
        voice = voice or os.getenv("TTS_API_VOICE", "alloy")
        model = os.getenv("TTS_API_MODEL", "tts-1")
        try:
            response = self._api_client.audio.speech.create(
                model=model,
                voice=voice,
                input=text,
                response_format="mp3",
            )
            return response.content
        except Exception as e:
            logger.error(f"TTS API error: {e}")
            return b""

    def _synthesize_tts_ai(self, text: str, voice: Optional[str]) -> bytes:
        voice = voice or os.getenv("TTS_AI_VOICE", "af_bella")
        model = os.getenv("TTS_AI_MODEL", "kokoro")
        try:
            response = self._tts_ai_client.audio.speech.create(
                model=model,
                voice=voice,
                input=text
            )
            return response.content
        except Exception as e:
            logger.error(f"TTS tts.ai error: {e}")
            return b""

    def _synthesize_freetts(self, text: str, voice: Optional[str]) -> bytes:
        import requests
        import time
        voice = voice or "zh-CN-XiaoxiaoNeural"
        
        # FreeTTS has a 1000 char limit
        if len(text) > 1000:
            logger.warning(f"FreeTTS text too long ({len(text)} chars), truncating to 1000")
            text = text[:1000]

        url_tts = "https://freetts.org/api/tts"
        headers = {
            "Content-Type": "application/json"
        }
        payload = {
            "text": text,
            "voice": voice,
            "rate": "+0%",
            "pitch": "+0Hz"
        }
        
        try:
            response = requests.post(url_tts, headers=headers, json=payload)
            if response.status_code == 200:
                res_data = response.json()
                file_id = res_data.get("file_id")
                if not file_id:
                    logger.error(f"FreeTTS returned no file_id: {res_data}")
                    return b""
                
                download_url = f"https://freetts.org/api/audio/{file_id}"
                time.sleep(1) 
                
                audio_response = requests.get(download_url)
                if audio_response.status_code == 200:
                    return audio_response.content
                else:
                    logger.error(f"FreeTTS download failed, status: {audio_response.status_code}")
            else:
                logger.error(f"FreeTTS synthesis failed, status: {response.status_code} - {response.text}")
        except Exception as e:
            logger.error(f"FreeTTS exception: {e}")
            
        return b""

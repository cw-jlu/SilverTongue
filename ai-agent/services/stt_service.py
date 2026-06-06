"""
Speech-to-Text Service
========================
Dual-engine STT with automatic fallback:
    1. Local ``faster-whisper`` (requires GPU, ~4 GB VRAM)
    2. Remote API (OpenAI Whisper API / compatible)

The engine is selected once at init time based on GPU availability.
"""

import io
import os
import tempfile
from typing import Optional
from loguru import logger


class STTService:
    """
    Transcribe raw audio bytes → text.

    Usage::

        stt = STTService()
        text = stt.transcribe(audio_bytes, language="en")
    """

    def __init__(self):
        self.engine: str = "none"
        self._whisper_model = None
        self._api_client = None

        # Try local first, then API
        if self._init_local_whisper():
            self.engine = "local"
        elif self._init_api_whisper():
            self.engine = "api"
        else:
            logger.warning("STTService: no engine available — transcribe will return empty")

        logger.info(f"STTService initialised with engine={self.engine}")

    # ---- engine init -------------------------------------------------------

    def _init_local_whisper(self) -> bool:
        """Try to load faster-whisper with GPU."""
        if os.getenv("STT_FORCE_API", "").lower() == "true":
            return False
        try:
            import torch
            if not torch.cuda.is_available():
                logger.info("STT: CUDA not available, skipping local whisper")
                return False
        except ImportError:
            return False

        try:
            from faster_whisper import WhisperModel
            model_size = os.getenv("STT_MODEL_SIZE", "large-v3")
            self._whisper_model = WhisperModel(
                model_size,
                device="cuda",
                compute_type="float16",
            )
            logger.info(f"STT: loaded faster-whisper model={model_size} on GPU")
            return True
        except Exception as e:
            logger.warning(f"STT: failed to init local whisper: {e}")
            return False

    def _init_api_whisper(self) -> bool:
        """Fall back to OpenAI Whisper API."""
        api_key = os.getenv("STT_API_KEY", os.getenv("LLM_API_KEY", ""))
        if not api_key:
            logger.info("STT: no API key for remote whisper")
            return False
        try:
            from openai import OpenAI
            base_url = os.getenv("STT_API_BASE_URL", "https://api.openai.com/v1")
            self._api_client = OpenAI(api_key=api_key, base_url=base_url)
            logger.info(f"STT: using remote Whisper API at {base_url}")
            return True
        except Exception as e:
            logger.warning(f"STT: failed to init API client: {e}")
            return False

    # ---- public API --------------------------------------------------------

    def transcribe(self, audio_data: bytes, language: str = "en") -> str:
        """
        Transcribe audio bytes to text.

        Args:
            audio_data: Raw WAV/PCM bytes.
            language: BCP-47 language hint (default ``en``).

        Returns:
            Transcribed text string (empty on failure).
        """
        if not audio_data:
            return ""

        if self.engine == "local":
            return self._transcribe_local(audio_data, language)
        elif self.engine == "api":
            return self._transcribe_api(audio_data, language)
        else:
            logger.warning("STT: no engine available")
            return ""

    # ---- engine implementations --------------------------------------------

    def _transcribe_local(self, audio_data: bytes, language: str) -> str:
        try:
            # faster-whisper expects a file path or file-like object
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
                f.write(audio_data)
                f.flush()
                tmp_path = f.name

            segments, info = self._whisper_model.transcribe(
                tmp_path, language=language, beam_size=5,
            )
            text = " ".join(seg.text.strip() for seg in segments)
            logger.debug(f"STT local: detected={info.language} prob={info.language_probability:.2f}")
            return text
        except Exception as e:
            logger.error(f"STT local transcription error: {e}")
            return ""
        finally:
            try:
                os.unlink(tmp_path)
            except Exception:
                pass

    def _transcribe_api(self, audio_data: bytes, language: str) -> str:
        try:
            audio_file = io.BytesIO(audio_data)
            audio_file.name = "audio.wav"
            model = os.getenv("STT_API_MODEL", "whisper-1")
            result = self._api_client.audio.transcriptions.create(
                model=model,
                file=audio_file,
                language=language,
            )
            return result.text.strip()
        except Exception as e:
            logger.error(f"STT API transcription error: {e}")
            return ""

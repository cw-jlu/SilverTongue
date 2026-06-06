"""
Local Model Provider (Ollama / vLLM)
======================================
Runs inference against a locally-hosted model.
Automatically detects CUDA availability and total VRAM.
If the machine has insufficient resources, the provider
refuses to register (health_check returns False).

Capability: TEXT_ONLY (default) or VOICE_INPUT depending on model
"""

from typing import Iterator, List, Dict, Optional
from loguru import logger

from services.providers.base import (
    ModelProvider, ModelConfig, ModelCapability, ModelResponse,
)


class LocalProvider(ModelProvider):
    """
    Connects to a local inference server (Ollama on :11434 or vLLM on :8000).
    """

    DEFAULT_MIN_VRAM_GB = 200.0

    def __init__(self, config: ModelConfig):
        super().__init__(config)
        self.min_vram_gb = config.extra.get("min_vram_gb", self.DEFAULT_MIN_VRAM_GB)
        self.endpoint = config.endpoint or "http://localhost:11434"  # Ollama default
        self._gpu_ok = self._detect_gpu()
        self._client = None

    # ---- GPU detection -----------------------------------------------------

    def _detect_gpu(self) -> bool:
        """
        Check whether the host has CUDA + enough VRAM.
        Returns False (and logs a warning) if requirements are not met.
        """
        try:
            import torch
        except ImportError:
            logger.warning(f"[{self.name}] torch not installed — cannot detect GPU")
            return False

        if not torch.cuda.is_available():
            logger.warning(f"[{self.name}] CUDA not available")
            return False

        device_count = torch.cuda.device_count()
        total_vram_gb = 0.0
        for i in range(device_count):
            props = torch.cuda.get_device_properties(i)
            vram_gb = props.total_mem / (1024 ** 3)
            total_vram_gb += vram_gb
            logger.info(f"[{self.name}] GPU {i}: {props.name} — {vram_gb:.1f} GB VRAM")

        if total_vram_gb < self.min_vram_gb:
            logger.warning(
                f"[{self.name}] Total VRAM {total_vram_gb:.1f} GB < required {self.min_vram_gb} GB"
            )
            return False

        logger.info(f"[{self.name}] GPU check passed: {total_vram_gb:.1f} GB total VRAM")
        return True

    # ---- lazy client init --------------------------------------------------

    def _get_client(self):
        if self._client is not None:
            return self._client
        if not self._gpu_ok:
            return None
        try:
            from openai import OpenAI
            # Ollama and vLLM both expose an OpenAI-compatible /v1 endpoint
            base_url = self.endpoint.rstrip("/")
            if not base_url.endswith("/v1"):
                base_url += "/v1"
            self._client = OpenAI(api_key="not-needed", base_url=base_url)
            return self._client
        except Exception as e:
            logger.error(f"[{self.name}] Failed to create local client: {e}")
            return None

    # ---- ModelProvider implementation --------------------------------------

    def chat(
        self,
        messages: List[Dict[str, str]],
        audio_data: Optional[bytes] = None,
        **kwargs,
    ) -> ModelResponse:
        client = self._get_client()
        if client is None:
            return ModelResponse(
                text="[ERROR] Local model not available (GPU requirements not met)",
                finish_reason="error",
            )

        try:
            resp = client.chat.completions.create(
                model=self.model_name,
                messages=messages,
                temperature=kwargs.get("temperature", 0.7),
                max_tokens=kwargs.get("max_tokens", self.config.max_tokens),
            )
            choice = resp.choices[0]
            return ModelResponse(
                text=choice.message.content or "",
                finish_reason=choice.finish_reason or "stop",
                raw=resp,
            )
        except Exception as e:
            logger.error(f"[{self.name}] chat error: {e}")
            return ModelResponse(text=f"[ERROR] {e}", finish_reason="error")

    def stream_chat(
        self,
        messages: List[Dict[str, str]],
        audio_data: Optional[bytes] = None,
        **kwargs,
    ) -> Iterator[ModelResponse]:
        client = self._get_client()
        if client is None:
            yield ModelResponse(text="[ERROR] Local model not available", finish_reason="error")
            return

        try:
            stream = client.chat.completions.create(
                model=self.model_name,
                messages=messages,
                temperature=kwargs.get("temperature", 0.7),
                max_tokens=kwargs.get("max_tokens", self.config.max_tokens),
                stream=True,
            )
            for chunk in stream:
                delta = chunk.choices[0].delta if chunk.choices else None
                if delta and delta.content:
                    yield ModelResponse(
                        text=delta.content,
                        finish_reason=chunk.choices[0].finish_reason or "",
                    )
        except Exception as e:
            logger.error(f"[{self.name}] stream_chat error: {e}")
            yield ModelResponse(text=f"[ERROR] {e}", finish_reason="error")

    def health_check(self) -> bool:
        if not self._gpu_ok:
            self.is_healthy = False
            return False
        client = self._get_client()
        if client is None:
            self.is_healthy = False
            return False
        try:
            client.models.list()
            self.is_healthy = True
            logger.info(f"[{self.name}] health_check OK (local)")
            return True
        except Exception as e:
            logger.warning(f"[{self.name}] health_check failed: {e}")
            self.is_healthy = False
            return False

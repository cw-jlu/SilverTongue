"""
OpenAI-Compatible API Provider
================================
Covers any endpoint that speaks the OpenAI Chat Completions API:
OpenAI GPT-4o, DeepSeek-Chat, Qwen-text, Moonshot, etc.

Capability: TEXT_ONLY
"""

from typing import Iterator, List, Dict, Optional
from loguru import logger

from services.providers.base import (
    ModelProvider, ModelConfig, ModelCapability, ModelResponse,
)


class OpenAIProvider(ModelProvider):

    def __init__(self, config: ModelConfig):
        # Force capability to TEXT_ONLY for this provider type
        config.capability = ModelCapability.TEXT_ONLY
        super().__init__(config)
        self._client = None

    # ---- lazy client init --------------------------------------------------

    def _get_client(self):
        if self._client is not None:
            return self._client
        try:
            from openai import OpenAI
            self._client = OpenAI(
                api_key=self.config.api_key,
                base_url=self.config.endpoint or "https://api.openai.com/v1",
            )
            return self._client
        except Exception as e:
            logger.error(f"[{self.name}] Failed to create OpenAI client: {e}")
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
            return ModelResponse(text="[ERROR] OpenAI client not available", finish_reason="error")

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
                usage={
                    "prompt_tokens": resp.usage.prompt_tokens if resp.usage else 0,
                    "completion_tokens": resp.usage.completion_tokens if resp.usage else 0,
                },
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
            yield ModelResponse(text="[ERROR] OpenAI client not available", finish_reason="error")
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
        client = self._get_client()
        if client is None:
            self.is_healthy = False
            return False
        try:
            # Lightweight probe: list models or send a tiny prompt
            client.models.list()
            self.is_healthy = True
            logger.info(f"[{self.name}] health_check OK")
            return True
        except Exception as e:
            logger.warning(f"[{self.name}] health_check failed: {e}")
            self.is_healthy = False
            return False

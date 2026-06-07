"""
Qwen 2.5-Omni Voice-Native Provider
======================================
Uses the DashScope / OpenAI-compatible multi-modal API to send
both text and audio, and receive text + audio in the response.

Capability: VOICE_FULL
"""

import base64
from typing import Iterator, List, Dict, Optional
from loguru import logger

from services.providers.base import (
    ModelProvider, ModelConfig, ModelCapability, ModelResponse,
)


class QwenOmniProvider(ModelProvider):

    def __init__(self, config: ModelConfig):
        config.capability = ModelCapability.VOICE_FULL
        super().__init__(config)
        self._client = None

    def _get_client(self):
        if self._client is not None:
            return self._client
        try:
            from openai import OpenAI
            endpoint = self.config.endpoint or "https://dashscope.aliyuncs.com/compatible-mode/v1"
            self._client = OpenAI(
                api_key=self.config.api_key,
                base_url=endpoint,
            )
            return self._client
        except Exception as e:
            logger.error(f"[{self.name}] Failed to create DashScope client: {e}")
            return None

    # ---- helpers -----------------------------------------------------------

    @staticmethod
    def _build_audio_content(text: str, audio_data: Optional[bytes]) -> list:
        """
        Build a multi-modal content array with text and optional audio.
        DashScope Qwen2.5-Omni accepts:
            {"type": "input_audio", "input_audio": {"data": "<base64>", "format": "wav"}}
        """
        parts = []
        if audio_data:
            b64 = base64.b64encode(audio_data).decode("utf-8")
            parts.append({
                "type": "input_audio",
                "input_audio": {"data": f"data:audio/wav;base64,{b64}", "format": "wav"},
            })
        parts.append({"type": "text", "text": text})
        return parts

    @staticmethod
    def _extract_audio_from_response(raw_resp) -> bytes:
        """
        Extract audio bytes from a DashScope multi-modal response.
        The audio is returned as base64 in the response choices.
        """
        try:
            choice = raw_resp.choices[0]
            # DashScope returns audio in choice.message.audio or as base64 in content
            if hasattr(choice.message, "audio") and choice.message.audio:
                audio_info = choice.message.audio
                if isinstance(audio_info, dict) and "data" in audio_info:
                    return base64.b64decode(audio_info["data"])
            # Fallback: try to find audio block in content
            if isinstance(choice.message.content, list):
                for block in choice.message.content:
                    if isinstance(block, dict) and block.get("type") == "audio":
                        data = block.get("audio", {}).get("data", "")
                        if data:
                            return base64.b64decode(data)
        except Exception as e:
            logger.warning(f"Could not extract audio from response: {e}")
        return b""

    # ---- ModelProvider implementation --------------------------------------

    def chat(
        self,
        messages: List[Dict[str, str]],
        audio_data: Optional[bytes] = None,
        **kwargs,
    ) -> ModelResponse:
        client = self._get_client()
        if client is None:
            return ModelResponse(text="[ERROR] DashScope client not available", finish_reason="error")

        try:
            # Rebuild the last user message to include audio
            api_messages = []
            for msg in messages:
                if msg["role"] == "user" and msg is messages[-1] and audio_data:
                    api_messages.append({
                        "role": "user",
                        "content": self._build_audio_content(msg["content"], audio_data),
                    })
                else:
                    api_messages.append(msg)

            extra_params = {}
            # Enable audio output on models that support it
            modalities = kwargs.get("modalities", ["text", "audio"])
            extra_params["modalities"] = modalities
            audio_params = kwargs.get("audio", {"voice": "Cherry", "format": "wav"})
            extra_params["audio"] = audio_params

            resp = client.chat.completions.create(
                model=self.model_name or "qwen2.5-omni",
                messages=api_messages,
                temperature=kwargs.get("temperature", 0.7),
                max_tokens=kwargs.get("max_tokens", self.config.max_tokens),
                **extra_params,
            )

            choice = resp.choices[0]
            # Extract text: could be a string or a list of content blocks
            text = ""
            if isinstance(choice.message.content, str):
                text = choice.message.content
            elif isinstance(choice.message.content, list):
                text = "".join(
                    b.get("text", "") for b in choice.message.content
                    if isinstance(b, dict) and b.get("type") == "text"
                )

            audio_bytes = self._extract_audio_from_response(resp)

            return ModelResponse(
                text=text,
                audio_data=audio_bytes,
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
        """
        Stream text and audio.
        """
        client = self._get_client()
        if client is None:
            yield ModelResponse(text="[ERROR] DashScope client not available", finish_reason="error")
            return

        try:
            api_messages = []
            for msg in messages:
                if msg["role"] == "user" and msg is messages[-1] and audio_data:
                    api_messages.append({
                        "role": "user",
                        "content": self._build_audio_content(msg["content"], audio_data),
                    })
                else:
                    api_messages.append(msg)

            extra_params = {}
            modalities = kwargs.get("modalities", ["text", "audio"])
            extra_params["modalities"] = modalities
            audio_params = kwargs.get("audio", {"voice": "Cherry", "format": "wav"})
            extra_params["audio"] = audio_params

            resp_stream = client.chat.completions.create(
                model=self.model_name or "qwen2.5-omni",
                messages=api_messages,
                temperature=kwargs.get("temperature", 0.7),
                max_tokens=kwargs.get("max_tokens", self.config.max_tokens),
                stream=True,
                stream_options={"include_usage": True},
                **extra_params,
            )

            for chunk in resp_stream:
                if not chunk.choices:
                    continue
                choice = chunk.choices[0]
                text_delta = ""
                if choice.delta and hasattr(choice.delta, "content") and choice.delta.content:
                    text_delta = choice.delta.content

                audio_bytes = b""
                if choice.delta and hasattr(choice.delta, "audio") and choice.delta.audio:
                    audio_info = choice.delta.audio
                    if isinstance(audio_info, dict) and "data" in audio_info:
                        audio_bytes = base64.b64decode(audio_info["data"])

                yield ModelResponse(
                    text=text_delta,
                    audio_data=audio_bytes,
                    finish_reason=choice.finish_reason or "",
                    usage={},
                    raw=chunk,
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
            client.models.list()
            self.is_healthy = True
            logger.info(f"[{self.name}] health_check OK (DashScope)")
            return True
        except Exception as e:
            logger.warning(f"[{self.name}] health_check failed: {e}")
            self.is_healthy = False
            return False

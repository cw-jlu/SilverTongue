"""
Model Provider Abstraction Layer
=================================
Defines the contract that all model providers must implement,
along with shared data types for capabilities and responses.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from typing import Iterator, List, Dict, Any, Optional


# ---------------------------------------------------------------------------
# Enums & Data Classes
# ---------------------------------------------------------------------------

class ModelCapability(Enum):
    """What kind of I/O a model supports."""
    TEXT_ONLY = "text_only"          # text in → text out
    VOICE_INPUT = "voice_input"     # text+audio in → text out
    VOICE_FULL = "voice_full"       # text+audio in → text+audio out  (e.g. Qwen2.5-Omni)


@dataclass
class ModelResponse:
    """Unified response from any model provider."""
    text: str = ""                          # generated text
    audio_data: bytes = b""                 # generated audio (empty for text-only models)
    finish_reason: str = "stop"             # stop / length / error
    usage: Dict[str, int] = field(default_factory=dict)   # prompt_tokens, completion_tokens
    raw: Any = None                         # provider-specific raw response


@dataclass
class ModelConfig:
    """Configuration for a single model provider instance."""
    name: str                               # unique display name, e.g. "qwen2.5-omni"
    provider_type: str                      # "openai" | "qwen_omni" | "local"
    capability: ModelCapability = ModelCapability.TEXT_ONLY
    endpoint: str = ""                      # API base URL or local inference endpoint
    api_key: str = ""
    model_name: str = ""                    # model identifier sent to the API
    priority: int = 100                     # lower = higher priority
    max_tokens: int = 1024
    is_active: bool = True
    extra: Dict[str, Any] = field(default_factory=dict)


# ---------------------------------------------------------------------------
# Abstract Base
# ---------------------------------------------------------------------------

class ModelProvider(ABC):
    """
    Base class for all model backends (remote API, local GPU, etc.).

    Lifecycle:
        1. __init__(config)  —  parse config, prepare client
        2. health_check()    —  verify the backend is reachable
        3. chat / stream_chat —  generate a response
    """

    def __init__(self, config: ModelConfig):
        self.config = config
        self.name = config.name
        self.model_name = config.model_name or config.name
        self.capability = config.capability
        self.priority = config.priority
        self.is_healthy = False

    # ---- Required overrides ------------------------------------------------

    @abstractmethod
    def chat(
        self,
        messages: List[Dict[str, str]],
        audio_data: Optional[bytes] = None,
        **kwargs,
    ) -> ModelResponse:
        """Single-turn (non-streaming) generation."""

    @abstractmethod
    def stream_chat(
        self,
        messages: List[Dict[str, str]],
        audio_data: Optional[bytes] = None,
        **kwargs,
    ) -> Iterator[ModelResponse]:
        """Streaming generation – yield partial ModelResponse chunks."""

    @abstractmethod
    def health_check(self) -> bool:
        """Return True if the backend is reachable and ready."""

    # ---- Convenience helpers -----------------------------------------------

    @property
    def supports_voice(self) -> bool:
        return self.capability in (ModelCapability.VOICE_INPUT, ModelCapability.VOICE_FULL)

    @property
    def returns_audio(self) -> bool:
        return self.capability == ModelCapability.VOICE_FULL

    def __repr__(self) -> str:
        return (
            f"<{self.__class__.__name__} name={self.name!r} "
            f"model={self.model_name!r} cap={self.capability.value} "
            f"pri={self.priority} healthy={self.is_healthy}>"
        )

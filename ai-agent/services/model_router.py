"""
Model Router
==============
Central registry that discovers, health-checks and selects model providers.

Configuration sources (checked in order):
1. ``MODEL_CONFIGS_JSON`` env var  —  a JSON array of ModelConfig dicts
2. Individual env vars  —  ``LLM_API_KEY``, ``LLM_BASE_URL``, etc.

If a local GPU with sufficient VRAM is detected, a LocalProvider is
registered automatically. Otherwise it is silently skipped.
"""

import json
import os
from typing import List, Optional
from loguru import logger

from services.providers.base import (
    ModelCapability, ModelConfig, ModelProvider,
)
from services.providers.openai_provider import OpenAIProvider
from services.providers.qwen_omni_provider import QwenOmniProvider
from services.providers.local_provider import LocalProvider


# ---------------------------------------------------------------------------
# Provider factory
# ---------------------------------------------------------------------------

_PROVIDER_CLASSES = {
    "openai": OpenAIProvider,
    "qwen_omni": QwenOmniProvider,
    "local": LocalProvider,
}

_CAPABILITY_MAP = {
    "text_only": ModelCapability.TEXT_ONLY,
    "voice_input": ModelCapability.VOICE_INPUT,
    "voice_full": ModelCapability.VOICE_FULL,
}


def _build_provider(cfg: dict) -> Optional[ModelProvider]:
    """Instantiate a provider from a config dict. Returns None on failure."""
    provider_type = cfg.get("provider_type", "openai")
    cls = _PROVIDER_CLASSES.get(provider_type)
    if cls is None:
        logger.warning(f"Unknown provider_type: {provider_type}")
        return None

    cap_str = cfg.get("capability", "text_only")
    capability = _CAPABILITY_MAP.get(cap_str, ModelCapability.TEXT_ONLY)

    config = ModelConfig(
        name=cfg.get("name", cfg.get("model_name", "unnamed")),
        provider_type=provider_type,
        capability=capability,
        endpoint=cfg.get("endpoint", ""),
        api_key=cfg.get("api_key", ""),
        model_name=cfg.get("model_name", ""),
        priority=cfg.get("priority", 100),
        max_tokens=cfg.get("max_tokens", 1024),
        is_active=cfg.get("is_active", True),
        extra=cfg.get("extra", {}),
    )

    if not config.is_active:
        logger.info(f"Skipping inactive provider: {config.name}")
        return None

    try:
        return cls(config)
    except Exception as e:
        logger.error(f"Failed to create provider {config.name}: {e}")
        return None


# ---------------------------------------------------------------------------
# Default config from env vars (backward-compatible with previous single-LLM)
# ---------------------------------------------------------------------------

def _default_configs_from_env() -> List[dict]:
    """
    Build a minimal config list from individual environment variables.
    This keeps backward compatibility for simple deployments.
    """
    configs: List[dict] = []

    # 1. Try to register a local model
    configs.append({
        "name": "local-gpu",
        "provider_type": "local",
        "capability": "text_only",
        "endpoint": os.getenv("LOCAL_MODEL_ENDPOINT", "http://localhost:11434"),
        "model_name": os.getenv("LOCAL_MODEL_NAME", "qwen2.5:72b"),
        "priority": 10,
        "max_tokens": 2048,
        "extra": {"min_vram_gb": float(os.getenv("LOCAL_MIN_VRAM_GB", "200"))},
    })

    # 2. Qwen2.5-Omni (voice-native) via DashScope
    qwen_key = os.getenv("QWEN_OMNI_API_KEY", "")
    if qwen_key:
        configs.append({
            "name": "qwen2.5-omni",
            "provider_type": "qwen_omni",
            "capability": "voice_full",
            "endpoint": os.getenv("QWEN_OMNI_ENDPOINT", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            "api_key": qwen_key,
            "model_name": os.getenv("QWEN_OMNI_MODEL", "qwen2.5-omni"),
            "priority": 20,
            "max_tokens": 1024,
        })

    # 3. Generic OpenAI-compatible API (text-only fallback)
    llm_key = os.getenv("LLM_API_KEY", "")
    if llm_key:
        configs.append({
            "name": os.getenv("LLM_MODEL", "gpt-4o"),
            "provider_type": "openai",
            "capability": "text_only",
            "endpoint": os.getenv("LLM_BASE_URL", "https://api.openai.com/v1"),
            "api_key": llm_key,
            "model_name": os.getenv("LLM_MODEL", "gpt-4o"),
            "priority": 50,
            "max_tokens": 1024,
        })

    # 4. DeepSeek (text-only, cheap)
    ds_key = os.getenv("DEEPSEEK_API_KEY", "")
    if ds_key:
        configs.append({
            "name": "deepseek-chat",
            "provider_type": "openai",
            "capability": "text_only",
            "endpoint": os.getenv("DEEPSEEK_ENDPOINT", "https://api.deepseek.com/v1"),
            "api_key": ds_key,
            "model_name": os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
            "priority": 60,
            "max_tokens": 2048,
        })

    return configs


# ---------------------------------------------------------------------------
# Router
# ---------------------------------------------------------------------------

class ModelRouter:
    """
    Manages multiple model providers and selects the best one per request.

    Selection strategy:
        1. Filter to healthy providers
        2. If ``prefer_voice`` and a VOICE_FULL provider is available → use it
        3. Otherwise → lowest priority number wins
    """

    def __init__(self):
        self.providers: List[ModelProvider] = []
        self._load_configs()
        self._health_check_all()
        self._log_summary()

    # ---- init helpers ------------------------------------------------------

    def _load_configs(self):
        """Load provider configs from JSON env var or individual env vars."""
        raw_json = os.getenv("MODEL_CONFIGS_JSON", "")
        if raw_json:
            try:
                configs = json.loads(raw_json)
                logger.info(f"Loaded {len(configs)} model configs from MODEL_CONFIGS_JSON")
            except json.JSONDecodeError as e:
                logger.error(f"Invalid MODEL_CONFIGS_JSON: {e}")
                configs = _default_configs_from_env()
        else:
            configs = _default_configs_from_env()

        for cfg in configs:
            provider = _build_provider(cfg)
            if provider is not None:
                self.providers.append(provider)

    def _health_check_all(self):
        for p in self.providers:
            try:
                p.health_check()
            except Exception as e:
                logger.error(f"Health check exception for {p.name}: {e}")
                p.is_healthy = False

    def _log_summary(self):
        healthy = [p for p in self.providers if p.is_healthy]
        logger.info(f"ModelRouter: {len(healthy)}/{len(self.providers)} providers healthy")
        for p in self.providers:
            status = "✅" if p.is_healthy else "❌"
            logger.info(f"  {status} {p}")

    # ---- selection ---------------------------------------------------------

    def select(self, prefer_voice: bool = False) -> Optional[ModelProvider]:
        """
        Pick the best available provider.

        Args:
            prefer_voice: If True, prefer VOICE_FULL providers for native
                          audio I/O.  Falls back to text-only if none available.
        """
        available = [p for p in self.providers if p.is_healthy]
        if not available:
            logger.error("ModelRouter: no healthy providers available!")
            return None

        if prefer_voice:
            voice = [p for p in available if p.capability == ModelCapability.VOICE_FULL]
            if voice:
                return min(voice, key=lambda p: p.priority)

        return min(available, key=lambda p: p.priority)

    def select_by_name(self, name: str) -> Optional[ModelProvider]:
        """Select a specific provider by name (for admin/debug endpoints)."""
        for p in self.providers:
            if p.name == name and p.is_healthy:
                return p
        return None

    def list_providers(self) -> List[dict]:
        """Return a serializable list of all registered providers."""
        return [
            {
                "name": p.name,
                "model": p.model_name,
                "capability": p.capability.value,
                "priority": p.priority,
                "healthy": p.is_healthy,
            }
            for p in self.providers
        ]

    def refresh(self):
        """Re-run health checks on all providers."""
        self._health_check_all()
        self._log_summary()

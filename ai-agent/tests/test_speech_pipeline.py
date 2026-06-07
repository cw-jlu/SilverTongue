import os
import sys

sys.path.append(os.path.join(os.path.dirname(__file__), ".."))

from services.stt_service import STTService
from services.tts_service import TTSService


def test_stt_service_dispatches_to_selected_engine(monkeypatch):
    monkeypatch.setattr(STTService, "_init_stt_ai", lambda self: False)
    monkeypatch.setattr(STTService, "_init_local_whisper", lambda self: False)
    monkeypatch.setattr(STTService, "_init_api_whisper", lambda self: False)

    service = STTService()
    service.engine = "api"
    monkeypatch.setattr(service, "_transcribe_api", lambda audio, language: "mock transcript")

    assert service.transcribe(b"audio-bytes") == "mock transcript"
    assert service.transcribe(b"") == ""


def test_tts_service_dispatches_to_selected_engine(monkeypatch):
    monkeypatch.setattr(TTSService, "_init_tts_ai", lambda self: False)
    monkeypatch.setattr(TTSService, "_init_freetts", lambda self: False)
    monkeypatch.setattr(TTSService, "_init_edge_tts", lambda self: False)
    monkeypatch.setattr(TTSService, "_init_api_tts", lambda self: False)

    service = TTSService()
    service.engine = "edge_tts"
    monkeypatch.setattr(service, "_synthesize_edge", lambda text, voice: b"mock-mp3")

    assert service.synthesize("hello") == b"mock-mp3"
    assert service.synthesize("   ") == b""

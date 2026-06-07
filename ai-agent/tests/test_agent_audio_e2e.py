import os
import sys
import types

sys.path.append(os.path.join(os.path.dirname(__file__), ".."))

embedding_module = types.ModuleType("services.embedding")


class DummyVectorDBClient:
    def __init__(self):
        self.collection = None

    def search(self, user_id, query_text, top_k=3):
        return []


embedding_module.VectorDBClient = DummyVectorDBClient
sys.modules["services.embedding"] = embedding_module

from agent.graph import create_agent_graph
import agent.nodes as nodes
from services.providers.base import ModelCapability, ModelResponse


class FakeVoiceRouter:
    class Provider:
        name = "fake-audio"
        capability = ModelCapability.TEXT_ONLY
        supports_voice = False

        def chat(self, messages, audio_data=None, **kwargs):
            return ModelResponse(text="Thanks, let's continue.")

    def __init__(self):
        self.provider = self.Provider()

    def select(self, prefer_voice=False):
        return self.provider


class FakeSttService:
    def transcribe(self, audio_data, language="en"):
        return "This is my spoken answer."


class FakeTtsService:
    def synthesize(self, text, voice=None):
        return b"voice-bytes"


class FakeConversationStore:
    def persist_message(self, **kwargs):
        return None


def test_audio_pipeline_uses_stt_and_tts_without_real_backends():
    nodes.vector_db.collection = None
    nodes.configure_nodes(
        FakeVoiceRouter(),
        FakeSttService(),
        FakeTtsService(),
        FakeConversationStore(),
    )

    app = create_agent_graph()
    result_state = app.invoke(
        {
            "session_id": "audio-session-1",
            "user_id": "user-audio",
            "mode": "free_talk",
            "user_level": "B1",
            "topic": "Daily Greeting",
            "context_text": None,
            "messages": [],
            "current_audio_buffer": b"\xff\xfe\xfa\xfb",
            "is_user_speaking": False,
            "turn_taken": True,
            "active_skills": [],
            "chinglish_analysis": {},
            "refined_text": None,
            "user_transcript": "",
            "response_audio": b"",
            "selected_provider": "",
            "model_capability": "",
            "next_node": "",
        }
    )

    assert result_state["user_transcript"] == "This is my spoken answer."
    assert result_state["messages"][-1]["content"] == "Thanks, let's continue."
    assert result_state["response_audio"] == b"voice-bytes"

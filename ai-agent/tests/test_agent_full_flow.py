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


class FakeProvider:
    name = "fake-text"
    capability = ModelCapability.TEXT_ONLY
    supports_voice = False

    def chat(self, messages, audio_data=None, **kwargs):
        return ModelResponse(text="Mock interview reply.")


class FakeRouter:
    def __init__(self):
        self.provider = FakeProvider()

    def select(self, prefer_voice=False):
        return self.provider


class FakeTtsService:
    def synthesize(self, text, voice=None):
        return b"mock-audio"


class FakeConversationStore:
    def __init__(self):
        self.records = []

    def persist_message(self, **kwargs):
        self.records.append(kwargs)


def test_full_agent_flow_runs_without_external_services():
    nodes.vector_db.collection = None
    store = FakeConversationStore()
    nodes.configure_nodes(FakeRouter(), None, FakeTtsService(), store)

    app = create_agent_graph()
    result_state = app.invoke(
        {
            "session_id": "test-session-123",
            "user_id": "user-tester",
            "mode": "free_talk",
            "user_level": "B2",
            "topic": "外企 HR 面试",
            "context_text": "Candidate has 5 years of backend experience.",
            "messages": [],
            "current_audio_buffer": b"Hello, I am here for the interview.",
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

    assert result_state["user_transcript"] == "Hello, I am here for the interview."
    assert result_state["messages"][-1]["content"] == "Mock interview reply."
    assert result_state["selected_provider"] == "fake-text"
    assert result_state["model_capability"] == "text_only"
    assert result_state["response_audio"] == b"mock-audio"
    assert len(store.records) == 2

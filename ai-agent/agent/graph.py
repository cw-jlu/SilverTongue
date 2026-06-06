"""
LangGraph State Graph — SilverTongue AI Agent
================================================

Flow:
    transcribe_audio
        → retrieve_context
            → analyze_chinglish
                → generate_reply
                    → [condition: needs_tts?]
                        ├── yes → synthesize_audio → END
                        └── no  → END
"""

from langgraph.graph import StateGraph, END
from agent.state import AgentState
from agent.nodes import (
    transcribe_audio,
    retrieve_context,
    analyze_chinglish,
    generate_reply,
    synthesize_audio,
)


def _needs_tts(state: AgentState) -> str:
    """
    Conditional edge: route to TTS if the model did NOT return audio.
    """
    if state.get("response_audio", b""):
        return "end"
    return "synthesize"


def create_agent_graph():
    """Build the LangGraph StateGraph for the SilverTongue AI Agent."""
    workflow = StateGraph(AgentState)

    # Add nodes
    workflow.add_node("transcribe_audio", transcribe_audio)
    workflow.add_node("retrieve_context", retrieve_context)
    workflow.add_node("analyze_chinglish", analyze_chinglish)
    workflow.add_node("generate_reply", generate_reply)
    workflow.add_node("synthesize_audio", synthesize_audio)

    # Linear edges
    workflow.add_edge("transcribe_audio", "retrieve_context")
    workflow.add_edge("retrieve_context", "analyze_chinglish")
    workflow.add_edge("analyze_chinglish", "generate_reply")

    # Conditional edge after generate_reply
    workflow.add_conditional_edges(
        "generate_reply",
        _needs_tts,
        {
            "synthesize": "synthesize_audio",
            "end": END,
        },
    )
    workflow.add_edge("synthesize_audio", END)

    # Entry point
    workflow.set_entry_point("transcribe_audio")

    return workflow.compile()


agent_graph = create_agent_graph()

from langgraph.graph import StateGraph, END
from agent.state import AgentState
from agent.nodes import retrieve_context, analyze_chinglish, generate_reply

def create_agent_graph():
    """
    Build the LangGraph StateGraph for the SilverTongue AI Agent.
    """
    workflow = StateGraph(AgentState)
    
    # Add nodes
    workflow.add_node("retrieve_context", retrieve_context)
    workflow.add_node("analyze_chinglish", analyze_chinglish)
    workflow.add_node("generate_reply", generate_reply)
    
    # Add edges
    workflow.add_edge("retrieve_context", "analyze_chinglish")
    workflow.add_edge("analyze_chinglish", "generate_reply")
    workflow.add_edge("generate_reply", END)
    
    # Set entry point
    workflow.set_entry_point("retrieve_context")
    
    # Compile graph
    app = workflow.compile()
    
    return app

agent_graph = create_agent_graph()

import os
import sys
import fitz

# Ensure the parent directory is in the path so we can import agent modules
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))

# Set up environment variables to ensure ModelRouter uses DeepSeek
os.environ["DEEPSEEK_API_KEY"] = "sk-46a31dc5807046e0bdef7d369365d724"
os.environ["DEEPSEEK_MODEL"] = "deepseek-chat"

from agent.graph import create_agent_graph
from agent.nodes import configure_nodes
from services.model_router import ModelRouter

def test_full_agent_flow_with_context():
    print("1. Parsing Context Document (1.pdf)...")
    test_pdf = os.path.join(os.path.dirname(__file__), '1.pdf')
    
    with open(test_pdf, 'rb') as f:
        file_data = f.read()
        
    doc = fitz.open(stream=file_data, filetype="pdf")
    context_text = "\n".join(page.get_text() for page in doc)
    doc.close()
    
    # Truncate just like in agent_service.py
    if len(context_text) > 10000:
        context_text = context_text[:10000]
    print(f"   ✅ Parsed {len(context_text)} characters.")

    print("\n2. Initializing Model Router...")
    router = ModelRouter()
    
    # Configure nodes with the router, but mock STT, TTS, and ConversationStore as None
    configure_nodes(router, None, None, None)

    print("\n3. Creating LangGraph application...")
    app = create_agent_graph()

    # The user says "Hello, I am here for the software engineering interview."
    user_input = "Hello, I am here for the software engineering interview. Let's begin."
    
    print(f"\n4. Preparing Agent State with Context...")
    print(f"   [User says]: {user_input}")
    
    initial_state = {
        "session_id": "test_session_123",
        "user_id": "user_tester",
        "mode": "free_talk",
        "user_level": "intermediate",
        "topic": "外企 HR 面试",
        "context_text": context_text,
        "messages": [{"role": "user", "content": user_input}],
        "current_audio_buffer": b"", # No audio, skip STT
        "is_user_speaking": False,
        "turn_taken": True,
        "chinglish_analysis": {},
        "refined_text": None,
        "user_transcript": user_input,
        "response_audio": b"",
    }

    print("\n5. Invoking Agent Graph...")
    result_state = app.invoke(initial_state)

    print("\n" + "="*50)
    print("✅ TEST COMPLETE")
    print("="*50)
    
    messages = result_state.get("messages", [])
    if len(messages) >= 2:
        ai_reply = messages[-1].get("content", "")
        print(f"AI Assistant Reply (HR Interviewer Role):\n")
        print(ai_reply)
    else:
        print("Error: No AI reply generated.")

if __name__ == "__main__":
    test_full_agent_flow_with_context()

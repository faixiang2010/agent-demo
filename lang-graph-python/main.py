import os
from pathlib import Path
from typing import TypedDict, Annotated
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

env_path = Path(__file__).parent / '.env'
load_dotenv(env_path)

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "YOUR_DEEPSEEK_API_KEY_HERE")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

class State(TypedDict):
    messages: Annotated[list, add_messages]

llm = ChatOpenAI(
    model=DEEPSEEK_MODEL,
    api_key=DEEPSEEK_API_KEY,
    base_url="https://api.deepseek.com",
    temperature=0.7
)

def chatbot(state: State):
    return {"messages": [llm.invoke(state["messages"])]}

graph_builder = StateGraph(State)
graph_builder.add_node("chatbot", chatbot)
graph_builder.add_edge(START, "chatbot")
graph_builder.add_edge("chatbot", END)
graph = graph_builder.compile()

if __name__ == "__main__":
    user_input = "你好，请介绍一下你自己"
    
    result = graph.invoke(
        {"messages": ("user", user_input)}
    )
    
    print("=" * 50)
    print(f"User: {user_input}")
    print("=" * 50)
    for message in result["messages"]:
        pretty_print = f"{message.type}: {message.content}"
        print(pretty_print)
    print("=" * 50)
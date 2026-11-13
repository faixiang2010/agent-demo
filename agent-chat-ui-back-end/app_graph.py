import os
from typing import Annotated, TypedDict
from datetime import datetime
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langchain_core.tools import tool
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode, tools_condition

load_dotenv()

class State(TypedDict):
    messages: Annotated[list, add_messages]

@tool
def get_current_time():
    """获取当前日期和时间"""
    now = datetime.now()
    return f"当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')}"

@tool
def calculator(expression: str) -> str:
    """计算数学表达式"""
    try:
        result = eval(expression)
        return f"计算结果: {expression} = {result}"
    except Exception as e:
        return f"计算错误: {str(e)}"

@tool
def search_knowledge(query: str) -> str:
    """搜索知识库"""
    knowledge_base = {
        "langchain": "LangChain 是一个用于构建 LLM 应用的框架",
        "langgraph": "LangGraph 是 LangChain 的扩展，用于构建有状态的 Agent 工作流",
        "python": "Python 是一种广泛使用的高级编程语言",
        "deepseek": "DeepSeek 是一家专注于大语言模型的中国公司"
    }
    
    query_lower = query.lower()
    for key, value in knowledge_base.items():
        if key in query_lower:
            return value
    
    return f"未找到关于 '{query}' 的信息"

def create_graph():
    api_key = os.getenv("DEEPSEEK_API_KEY")
    if not api_key:
        raise ValueError("❌ DEEPSEEK_API_KEY not found")

    model_name = os.getenv("DEEPSEEK_MODEL", "deepseek-flash")
    temperature = float(os.getenv("TEMPERATURE", "0.7"))

    llm = ChatOpenAI(
        model=model_name,
        api_key=api_key,
        base_url="https://api.deepseek.com",
        temperature=temperature
    )

    tools = [get_current_time, calculator, search_knowledge]
    llm_with_tools = llm.bind_tools(tools)

    def chatbot(state: State):
        return {"messages": [llm_with_tools.invoke(state["messages"])]}

    workflow = StateGraph(State)
    workflow.add_node("chatbot", chatbot)
    workflow.add_node("tools", ToolNode(tools))
    
    workflow.add_conditional_edges(
        "chatbot",
        tools_condition,
        {"tools": "tools", END: END}
    )
    workflow.add_edge("tools", "chatbot")
    
    workflow.add_edge(START, "chatbot")

    graph = workflow.compile()

    return graph

graph = create_graph()
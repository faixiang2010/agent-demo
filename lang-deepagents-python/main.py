import os
from pathlib import Path
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from deepagents import create_deep_agent

env_path = Path(__file__).parent / '.env'
load_dotenv(env_path)

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "YOUR_DEEPSEEK_API_KEY_HERE")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-flash")

llm = ChatOpenAI(
    model=DEEPSEEK_MODEL,
    api_key=DEEPSEEK_API_KEY,
    base_url="https://api.deepseek.com",
    temperature=0.7
)

def get_weather(city: str) -> str:
    """查询指定城市的天气"""
    weather_data = {
        "北京": "晴天，温度 25°C",
        "上海": "多云，温度 28°C",
        "广州": "阵雨，温度 30°C",
        "深圳": "晴转多云，温度 29°C",
        "武汉": "阴天，温度 26°C"
    }
    return weather_data.get(city, f"暂无 {city} 的天气信息")

def calculate(expression: str) -> str:
    """计算数学表达式"""
    try:
        result = eval(expression)
        return f"计算结果: {result}"
    except Exception as e:
        return f"计算错误: {str(e)}"

def get_current_time() -> str:
    """获取当前时间"""
    from datetime import datetime
    now = datetime.now()
    return f"当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')}"

agent = create_deep_agent(
    model=llm,
    tools=[get_weather, calculate, get_current_time],
    system_prompt="你是一个有帮助的 AI 助手，可以使用工具来查询天气、计算数学表达式和获取当前时间。请用中文回答用户的问题。"
)

if __name__ == "__main__":
    print("=== DeepAgents Demo ===\n")
    
    queries = [
        "请查询武汉当前天气?",
        "帮我计算 123 * 456 + 789 的结果",
        "现在几点了？",
        "查询北京和上海的天气，并告诉我哪个城市更热？"
    ]
    
    for i, query in enumerate(queries, 1):
        print(f"问题 {i}: {query}")
        print("-" * 50)
        
        try:
            response = agent.invoke({"input": query})
            if isinstance(response, dict) and 'messages' in response:
                messages = response['messages']
                if messages:
                    last_message = messages[-1]
                    print(f"回答: {last_message.content}\n")
            else:
                print(f"回答: {response}\n")
        except Exception as e:
            print(f"错误: {str(e)}\n")
        
        print("=" * 50 + "\n")
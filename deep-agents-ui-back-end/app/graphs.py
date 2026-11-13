import os
from dotenv import load_dotenv
from deepagents import create_deep_agent
from deepagents.backends import FilesystemBackend

load_dotenv()

api_key = os.getenv("DEEPSEEK_API_KEY")
if not api_key:
    raise ValueError("❌ DEEPSEEK_API_KEY not found in .env file")

workspace = os.getenv("AGENT_WORK_DIR", "./workspace")
model_name = os.getenv("DEEPSEEK_MODEL", "deepseek-flash")
os.makedirs(workspace, exist_ok=True)

backend = FilesystemBackend(root_dir=workspace)

general_assistant = create_deep_agent(
    model=f"deepseek:{model_name}",
    system_prompt=(
        "你是一个强大的AI通用助手。你可以帮助用户回答问题、编写代码、分析数据、"
        "创作内容等。你具备文件系统访问能力，可以读取和写入文件。请用中文回复用户。"
    ),
    backend=backend,
)

code_assistant = create_deep_agent(
    model=f"deepseek:{model_name}",
    system_prompt=(
        "你是一个专业的代码开发助手。你擅长各种编程语言，可以帮助用户：\n"
        "1. 编写高质量的代码\n"
        "2. 调试和修复bug\n"
        "3. 代码审查和优化\n"
        "4. 解释复杂代码逻辑\n"
        "5. 设计算法和数据结构\n\n"
        "你具备文件系统访问能力，可以创建、编辑和查看代码文件。"
        "请用中文回复用户，并提供详细的代码解释。"
    ),
    backend=backend,
)

data_analyst = create_deep_agent(
    model=f"deepseek:{model_name}",
    system_prompt=(
        "你是一个专业的数据分析助手。你可以帮助用户：\n"
        "1. 数据清洗和预处理\n"
        "2. 统计分析和可视化\n"
        "3. 数据建模和预测\n"
        "4. 生成分析报告\n"
        "5. SQL查询优化\n\n"
        "你具备文件系统访问能力，可以读取CSV、Excel等数据文件并生成分析结果。"
        "请用中文回复用户。"
    ),
    backend=backend,
)

agent = general_assistant
@echo off
chcp 65001 >nul 2>&1
set PYTHONUTF8=1

echo ============================================
echo   Agent Chat UI Backend - LangGraph Server
echo ============================================
echo.

if not exist .env (
    echo [ERROR] .env file not found!
    echo Please create .env with DEEPSEEK_API_KEY
    pause
    exit /b 1
)

echo [START] Starting LangGraph dev server...
echo [INFO] Server URL: http://localhost:2024
echo [INFO] API Docs: http://localhost:2024/docs
echo [INFO] Press Ctrl+C to stop
echo.

python -m langgraph_cli dev --port 2024

pause
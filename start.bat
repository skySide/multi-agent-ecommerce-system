@echo off
chcp 65001 >nul
echo ==========================================
echo   多 Agent 电商推荐系统 - 启动脚本
echo ==========================================
echo.

REM 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker 未启动，请先启动 Docker Desktop
    pause
    exit /b 1
)

echo [1/4] 启动基础设施服务 (MySQL, Redis, Milvus)...
docker-compose up -d mysql redis milvus
if errorlevel 1 (
    echo [错误] 启动 Docker 服务失败
    pause
    exit /b 1
)
echo [OK] 基础设施服务已启动
echo.

echo [2/4] 等待 MySQL 就绪 (约 30 秒)...
timeout /t 30 /nobreak >nul
echo [OK] MySQL 已就绪
echo.

echo [3/4] 启动 Embedding 服务 (Python)...
REM 检查 Python 服务是否已在运行
curl -s http://localhost:8000/health >nul 2>&1
if errorlevel 1 (
    start "Embedding Service" cmd /c "cd python && python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload"
    echo [OK] Embedding 服务已启动 (窗口: Embedding Service)
    timeout /t 5 /nobreak >nul
) else (
    echo [OK] Embedding 服务已在运行
)
echo.

echo [4/4] 启动 Java 后端服务...
cd java
start "Java Backend" cmd /c "mvn spring-boot:run"
echo [OK] Java 后端服务已启动 (窗口: Java Backend)
echo.

echo ==========================================
echo   所有服务已启动！
echo ==========================================
echo.
echo 服务地址:
echo   - Java API:     http://localhost:8080
echo   - Python API:   http://localhost:8000
echo   - MySQL:        localhost:3306
echo   - Redis:        localhost:6379
echo   - Milvus:       localhost:19530
echo.
echo 常用命令:
echo   - 查看日志: docker-compose logs -f
echo   - 停止服务: docker-compose down
echo   - 重启 Java: 关闭 Java Backend 窗口后重新运行 start.bat
echo.
pause

@echo off
setlocal
chcp 65001 >nul
title BridgeAgent 前端

set "PATH=C:\Program Files\nodejs;%PATH%"
cd /d "%~dp0web"

where npm >nul 2>&1
if errorlevel 1 (
  echo 未找到 npm。请先安装 Node.js LTS，或确认 C:\Program Files\nodejs 存在。
  pause
  exit /b 1
)

if not exist "node_modules\" (
  echo 首次启动，正在安装依赖...
  call npm install
  if errorlevel 1 (
    echo 依赖安装失败。
    pause
    exit /b 1
  )
)

echo 正在启动前端，浏览器将打开 http://127.0.0.1:5173/workbench
echo 关闭本窗口即停止前端服务。
echo.

start "" cmd /c "timeout /t 3 /nobreak >nul && start http://127.0.0.1:5173/workbench"

call npm run dev
echo.
echo 前端已停止。
pause

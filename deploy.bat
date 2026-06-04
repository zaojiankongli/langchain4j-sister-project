@echo off
chcp 65001 >nul
echo ====================================
echo  部署到 VM 192.168.100.128
echo ====================================
echo.

set VM=root@192.168.100.128
set JAR=target\langchain4j_sister_project-0.0.1-SNAPSHOT.jar
set DOCKER_COMPOSE=docker-compose.vm.yml
set NGINX_CONF=frontend\nginx.vm.conf
set FRONTEND_DIST=frontend\dist
set ENV_FILE=.env.vm

:: ── 1. 创建密钥文件（请先修改密钥！）──
echo 创建 .env.vm 模板，请修改里面的密钥...
(
echo DASHSCOPE_API_KEY=sk-请替换成你的key
echo JWT_SECRET=请替换成你的jwt密钥
echo TTS_VOICE=请替换成你的tts-voice-id
) > %ENV_FILE%
echo ✓ .env.vm 已创建，记得编辑填密钥！
echo.

:: ── 2. 检查文件是否存在 ──
if not exist %JAR% echo [错误] JAR 不存在，请先 mvn package & pause & exit /b
if not exist %DOCKER_COMPOSE% echo [错误] docker-compose.vm.yml 不存在 & pause & exit /b
if not exist %NGINX_CONF% echo [错误] nginx.vm.conf 不存在 & pause & exit /b
if not exist %FRONTEND_DIST%\index.html echo [错误] 前端 dist/index.html 不存在，请先 npm run build & pause & exit /b
echo ✓ 所有文件就绪
echo.

:: ── 3. 传后端 JAR ──
echo [1/4] 上传后端 JAR...
scp %JAR% %VM%:/root/app.jar
if %errorlevel% neq 0 echo [失败] & pause & exit /b
echo ✓

:: ── 4. 传 docker-compose ──
echo [2/4] 上传 docker-compose.yml...
scp %DOCKER_COMPOSE% %VM%:/root/docker-compose.yml
if %errorlevel% neq 0 echo [失败] & pause & exit /b
echo ✓

:: ── 5. 传 Nginx 配置 ──
echo [3/4] 上传 Nginx 配置...
scp %NGINX_CONF% %VM%:/etc/nginx/conf.d/default.conf
if %errorlevel% neq 0 echo [失败] & pause & exit /b
echo ✓

:: ── 6. 传前端 dist ──
echo [4/4] 上传前端静态文件...
scp -r %FRONTEND_DIST%\* %VM%:/usr/share/nginx/html/
if %errorlevel% neq 0 echo [失败] & pause & exit /b
echo ✓

:: ── 7. 传密钥文件 ──
echo [额外] 上传 .env.vm...
scp %ENV_FILE% %VM%:/root/.env.vm
echo ✓

echo.
echo ====================================
echo  全部上传完成！
echo.
echo  接下来 SSH 到 VM 执行:
echo  ssh %VM%
echo  cd /root
echo  docker compose --env-file .env.vm up -d
echo  nginx -s reload
echo ====================================
pause

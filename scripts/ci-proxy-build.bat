@echo off
REM ci-proxy-build.bat —— 本地代理编译 + 日志回传（Windows）
REM 用法：双击运行，或命令行执行 scripts\ci-proxy-build.bat
REM 流程：拉取远端分支 -> gradlew compileJava -> 把日志提交推回分支
setlocal
for /f %%i in ('git rev-parse --abbrev-ref HEAD') do set BRANCH=%%i
if not exist build-reports mkdir build-reports
set LOG=build-reports\compile-java.log

echo ==^> 1/4 拉取远端最新代码 origin %BRANCH%
git pull --ff-only origin %BRANCH% || (echo 拉取失败：请先处理本地未提交改动 & exit /b 1)

echo ==^> 2/4 编译 compileJava（首次可能下载依赖，耐心）
call gradlew.bat compileJava --console=plain --stacktrace > %LOG% 2>&1
set CODE=%ERRORLEVEL%

echo ==^> 3/4 结果: exit_code=%CODE% （日志: %LOG%）
powershell -command "Get-Content %LOG% -Tail 5"

echo ==^> 4/4 提交并推回分支
git add %LOG%
git commit -m "ci-proxy: compileJava exit=%CODE%"
git push origin %BRANCH%

echo 完成。沙箱侧将读取 %LOG% 并迭代修复。
exit /b %CODE%

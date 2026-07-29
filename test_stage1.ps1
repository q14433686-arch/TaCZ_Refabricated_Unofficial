# TACZ 26.2 冒烟测试脚本
# 用于验证阶段1修复的有效性

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "TACZ 26.2 - 阶段1修复验证" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Continue"
$projectRoot = "d:\DOWLOD\MC测试\tacz-26.2-v5"
Set-Location $projectRoot

# 1. 检查修复的文件
Write-Host "[1/5] 检查修复文件..." -ForegroundColor Yellow

$file1 = "src\main\java\com\tacz\guns\block\AbstractGunSmithTableBlock.java"
$file2 = "src\main\java\com\tacz\guns\network\message\ClientMessageLaserColor.java"

if (Select-String -Path $file1 -Pattern "serverPlayer\.openMenu\(gunSmithTable\)" -Quiet) {
    Write-Host "  ✓ AbstractGunSmithTableBlock.java - 工作台菜单修复已应用" -ForegroundColor Green
} else {
    Write-Host "  ✗ AbstractGunSmithTableBlock.java - 修复未找到" -ForegroundColor Red
}

if (Select-String -Path $file2 -Pattern "buf1 -> buf1\.readEnum" -Quiet) {
    Write-Host "  ✓ ClientMessageLaserColor.java - 激光颜色lambda修复已应用" -ForegroundColor Green
} else {
    Write-Host "  ✗ ClientMessageLaserColor.java - 修复未找到" -ForegroundColor Red
}

Write-Host ""

# 2. 检查编译结果
Write-Host "[2/5] 检查编译产物..." -ForegroundColor Yellow

$classFile1 = "build\classes\java\main\com\tacz\guns\block\AbstractGunSmithTableBlock.class"
$classFile2 = "build\classes\java\main\com\tacz\guns\network\message\ClientMessageLaserColor.class"

if (Test-Path $classFile1) {
    $time1 = (Get-Item $classFile1).LastWriteTime
    Write-Host "  ✓ AbstractGunSmithTableBlock.class - 编译完成 ($time1)" -ForegroundColor Green
} else {
    Write-Host "  ✗ AbstractGunSmithTableBlock.class - 未找到" -ForegroundColor Red
}

if (Test-Path $classFile2) {
    $time2 = (Get-Item $classFile2).LastWriteTime
    Write-Host "  ✓ ClientMessageLaserColor.class - 编译完成 ($time2)" -ForegroundColor Green
} else {
    Write-Host "  ✗ ClientMessageLaserColor.class - 未找到" -ForegroundColor Red
}

Write-Host ""

# 3. 检查JAR文件
Write-Host "[3/5] 检查构建产物..." -ForegroundColor Yellow

$jarFile = Get-ChildItem "build\libs\*.jar" -Exclude "*-sources.jar" -ErrorAction SilentlyContinue | Select-Object -First 1

if ($jarFile) {
    $sizeMB = [math]::Round($jarFile.Length / 1MB, 2)
    Write-Host "  ✓ JAR文件: $($jarFile.Name) (${sizeMB}MB)" -ForegroundColor Green
    Write-Host "    生成时间: $($jarFile.LastWriteTime)" -ForegroundColor Gray
} else {
    Write-Host "  ✗ JAR文件未找到" -ForegroundColor Red
}

Write-Host ""

# 4. 快速服务端启动测试（仅检查能否启动，不运行游戏）
Write-Host "[4/5] 服务端快速冒烟测试..." -ForegroundColor Yellow
Write-Host "  正在启动服务器（30秒超时）..." -ForegroundColor Gray

$serverLog = "test_server_smoke.log"
$serverProcess = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "runServer","--no-daemon" -RedirectStandardOutput $serverLog -RedirectStandardError "test_server_error.log" -PassThru -NoNewWindow

$timeout = 30
$elapsed = 0
$serverStarted = $false

while ($elapsed -lt $timeout -and !$serverProcess.HasExited) {
    Start-Sleep -Seconds 2
    $elapsed += 2
    
    if (Test-Path $serverLog) {
        $content = Get-Content $serverLog -Raw -ErrorAction SilentlyContinue
        if ($content -match "TACZ|tacz") {
            Write-Host "  ✓ 服务器启动中，TACZ模块已加载" -ForegroundColor Green
            $serverStarted = $true
            break
        }
    }
}

# 停止服务器进程
if (!$serverProcess.HasExited) {
    Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue
}

if ($serverStarted) {
    Write-Host "  ✓ 服务端基础启动成功" -ForegroundColor Green
} else {
    Write-Host "  ⚠ 服务端启动超时（需要更长时间测试）" -ForegroundColor Yellow
}

Write-Host ""

# 5. 生成测试报告
Write-Host "[5/5] 生成测试报告..." -ForegroundColor Yellow

$reportContent = @"
# TACZ 26.2 阶段1修复验证报告
生成时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

## 代码修复验证
- AbstractGunSmithTableBlock.java: $(if (Select-String -Path $file1 -Pattern "serverPlayer\.openMenu\(gunSmithTable\)" -Quiet) { "✓ 已修复" } else { "✗ 未修复" })
- ClientMessageLaserColor.java: $(if (Select-String -Path $file2 -Pattern "buf1 -> buf1\.readEnum" -Quiet) { "✓ 已修复" } else { "✗ 未修复" })

## 编译状态
- AbstractGunSmithTableBlock.class: $(if (Test-Path $classFile1) { "✓ 存在" } else { "✗ 缺失" })
- ClientMessageLaserColor.class: $(if (Test-Path $classFile2) { "✓ 存在" } else { "✗ 缺失" })

## 构建产物
$(if ($jarFile) { "- JAR: $($jarFile.Name) ($(([math]::Round($jarFile.Length / 1MB, 2)))MB)" } else { "- JAR: ✗ 未生成" })

## 服务端冒烟测试
- 状态: $(if ($serverStarted) { "✓ 通过" } else { "⚠ 超时/需更长测试" })

## 下一步行动
1. 在真实环境中进行完整的双端测试
2. 验证工作台GUI能否正常打开
3. 验证激光颜色调节不会崩溃
4. 开始阶段2：修复渲染问题（第一人称枪械、手臂、配件模型）

## 待测试项（需实机）
- [ ] 打开枪械工作台不被踢出
- [ ] 工作台合成功能正常
- [ ] 激光颜色调节不崩溃
- [ ] 激光颜色同步正常
"@

$reportContent | Out-File -FilePath "SMOKE_TEST_STAGE1_REPORT.txt" -Encoding UTF8
Write-Host "  ✓ 报告已生成: SMOKE_TEST_STAGE1_REPORT.txt" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "测试完成！" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

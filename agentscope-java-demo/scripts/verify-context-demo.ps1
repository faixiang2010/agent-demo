param(
    [string]$JavaPath = 'D:\GreenSoft\java\jdk-17.0.14\bin\java.exe',
    [string]$MavenPath = 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $JavaPath)
Push-Location $taskRoot
try {
    & $MavenPath -o clean compile dependency:build-classpath '-Dmdep.outputFile=target/context-test-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw '编译失败' }
    $taskClasspath = (Join-Path $taskRoot 'target/classes') + ';' + (Get-Content 'target/context-test-classpath.txt' -Raw).Trim()
    $taskTestRoot = Join-Path $taskRoot ('target/context-regression/' + [Guid]::NewGuid().ToString())

    function Run-Case([string]$Name, [string[]]$Lines, [string[]]$Expected) {
        $taskCaseDir = Join-Path $taskTestRoot $Name
        New-Item -ItemType Directory -Path $taskCaseDir -Force | Out-Null
        if ($Name -eq 'source-navigation') {
            $taskSourceRelative = 'src/main/java/com/example/agentscope/harness/context/ContextManagementDemo.java'
            $taskSourceCopy = Join-Path $taskCaseDir $taskSourceRelative
            New-Item -ItemType Directory -Path (Split-Path -Parent $taskSourceCopy) -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $taskRoot $taskSourceRelative) -Destination $taskSourceCopy
        }
        $taskStart = [System.Diagnostics.ProcessStartInfo]::new()
        $taskStart.FileName = $JavaPath
        $taskStart.WorkingDirectory = $taskCaseDir
        $taskStart.UseShellExecute = $false
        $taskStart.CreateNoWindow = $true
        $taskStart.RedirectStandardInput = $true
        $taskStart.RedirectStandardOutput = $true
        $taskStart.RedirectStandardError = $true
        $taskStart.StandardInputEncoding = [System.Text.UTF8Encoding]::new($false)
        $taskStart.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
        $taskStart.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)
        foreach ($taskArg in @('-Dfile.encoding=UTF-8', '--class-path', $taskClasspath,
                'com.example.agentscope.harness.context.ContextManagementDemo', '--offline')) {
            $taskStart.ArgumentList.Add($taskArg)
        }
        $taskProcess = [System.Diagnostics.Process]::new()
        $taskProcess.StartInfo = $taskStart
        try {
            $null = $taskProcess.Start()
            $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
            $taskErr = $taskProcess.StandardError.ReadToEndAsync()
            foreach ($taskLine in $Lines) { $taskProcess.StandardInput.WriteLine($taskLine) }
            $taskProcess.StandardInput.Close()
            if (-not $taskProcess.WaitForExit(30000)) {
                $taskProcess.Kill($true)
                throw "$Name 超过30秒未退出"
            }
            $taskOutput = $taskOut.GetAwaiter().GetResult() + $taskErr.GetAwaiter().GetResult()
            Set-Content -LiteralPath (Join-Path $taskCaseDir 'console.log') -Value $taskOutput -Encoding utf8
            if ($taskProcess.ExitCode -ne 0) { throw "$Name 退出码异常" }
            foreach ($taskNeedle in $Expected) {
                if (-not $taskOutput.Contains($taskNeedle)) { throw "$Name 缺少预期输出：$taskNeedle" }
            }
            Write-Output "PASS: $Name"
            return $taskCaseDir
        } finally { $taskProcess.Dispose() }
    }

    $taskAllInput = [System.Collections.Generic.List[string]]::new()
    for ($taskScene = 1; $taskScene -le 18; $taskScene++) {
        $taskAllInput.Add([string]$taskScene)
    }
    $taskAllInput.Add('0')
    $taskResult = @(Run-Case 'all-scenes' $taskAllInput.ToArray() @(
        '第一次模型输入包含编号=false', '第二次模型输入包含编号=true', '摘要消息=1',
        '捕获模拟溢出', '第一次包含版本A=true', '第二次包含版本B=true', '预期拦截',
        '一次请求内发生了多轮模型调用=true', '修复并复验工具恰好执行一次=true',
        '原生Todo提醒含未完成项=true', '框架已生成非空MEMORY.md=true', '已退出'))
    $taskAllDir = $taskResult[-1]
    $taskConsole = Get-Content -LiteralPath (Join-Path $taskAllDir 'console.log') -Raw
    if ($taskConsole.Contains('[本场景未完成]')) { throw '默认场景出现失败' }
    if ([regex]::Matches($taskConsole, '\[本场景完成\]').Count -ne 18) { throw '没有完成全部18个场景' }
    if ([regex]::Matches($taskConsole, '选择场景：').Count -ne 19) { throw '场景中出现了额外输入或未回到菜单' }
    for ($taskScene = 1; $taskScene -le 18; $taskScene++) {
        $taskPrefix = 'scenario' + $taskScene.ToString('00')
        if (-not $taskConsole.Contains('[代码入口] ' + $taskPrefix)) { throw "$taskPrefix 缺少源码导航" }
    }
    $taskReports = Join-Path $taskAllDir '.agentscope/harness-context/reports'
    $taskPrune = @(Get-ChildItem -LiteralPath (Join-Path $taskReports 'scene-10') -Recurse -Filter 'model-input-*.txt')
    if (-not ((Get-Content -LiteralPath $taskPrune[-1].FullName -Raw).Contains('chars pruned'))) { throw '未发生原生工具结果裁剪' }
    $taskTruncated = @(Get-ChildItem -LiteralPath (Join-Path $taskReports 'scene-11') -Recurse -Filter 'truncated-tool-history.txt')
    if (-not ((Get-Content -LiteralPath $taskTruncated[-1].FullName -Raw).Contains('[旧参数已截断]'))) { throw '未发生原生参数截断' }
    $taskOffload = @(Get-ChildItem -LiteralPath (Join-Path $taskAllDir '.agentscope/harness-context/workspace/scene-07') -Recurse -File |
        Where-Object { $_.FullName.Contains('large_tool_results') })
    if ($taskOffload.Count -eq 0) { throw '未生成工具卸载文件' }
    $taskDynamicState = @(Get-ChildItem -LiteralPath (Join-Path $taskReports 'scene-03') -Recurse -Filter 'agent-state.json')
    $taskState = Get-Content -LiteralPath $taskDynamicState[-1].FullName -Raw
    if ($taskState.Contains('demo-temporary-context')) { throw '临时来源被重复持久化到历史' }
    Write-Output 'PASS: 原生卸载、裁剪、截断、摘要和临时来源隔离'

    $null = Run-Case 'invalid-menu' @('bad','99','','-1','0') @('请输入0到18之间的整数','已退出')
    $null = Run-Case 'exit-menu' @('/exit') @('已退出')
    $null = Run-Case 'eof' @() @('已退出')
    $null = Run-Case 'source-comparison' @('5','0') @('[来源=成功] 加载成功','[来源=异常] 停止','[来源=超时] 停止',
        '[来源=空列表] 加载成功','[来源=Mono.empty] 停止','本地 OMIT','省略后输入仍包含通过状态=false','本场景完成')
    $null = Run-Case 'request-budget' @('8','0') @('输入超预算','被拦截请求到达Model.stream=false','放行后请求到达Model.stream=true','本场景完成')
    $taskNotes = @(Get-ChildItem -LiteralPath $taskReports -Recurse -Filter '学习小结.txt')
    if ($taskNotes.Count -ne 18) { throw '没有保存全部18个场景的小结' }
    $null = Run-Case 'real-tool-loop' @('16','0') @('一次请求内发生了多轮模型调用=true',
        '第一轮动态资料是失败状态=true','工具之后动态资料变成通过=true','修复并复验工具恰好执行一次=true',
        '实际输入含长日志卸载位置=true','动态来源被当作历史消息持久化=false','本场景完成')
    $null = Run-Case 'task-recovery' @('17','0') @('实例B恢复了计划模式=true','历史实际生成了摘要=true',
        '原生Todo实际保存了三项待办=true',
        '压缩前后结构化Todo完全一致=true','原生Todo提醒含未完成项=true','任务提醒被反复存成历史=false',
        '实际历史仍含旧todo_write回执=false','应用切回执行模式后计划模式仍开启=false','本场景完成')
    $null = Run-Case 'native-memory' @('18','0') @('原生JSONL档案含核对码=true','提取后有每日记忆文件=true',
        '框架已生成非空MEMORY.md=true','压缩工作历史仍含核对码=false','压缩后原生档案仍含核对码=true',
        '新的session与原会话不同=true','新会话输入包含长期偏好=true','新会话输入混入原核对码=false','本场景完成')
    $taskNav = @(Run-Case 'source-navigation' @('1','0') @('[源码位置]','ContextManagementDemo.java:','本场景完成'))
    $taskNavConsole = Get-Content -LiteralPath (Join-Path $taskNav[-1] 'console.log') -Raw
    if ($taskNavConsole.Contains('[源码导航，非异常]') -or $taskNavConsole.Contains("`tat com.example.agentscope")) {
        throw '仍然打印了伪异常堆栈'
    }
    $taskReplay = @(Run-Case 'repeat-scene' @('1','1','0') @('已退出'))
    $taskReplayConsole = Get-Content -LiteralPath (Join-Path $taskReplay[-1] 'console.log') -Raw
    if ([regex]::Matches($taskReplayConsole, '\[本场景完成\]').Count -ne 2) { throw '重跑同一数字没有正常完成' }
    $taskSessions = @(Get-ChildItem -LiteralPath (Join-Path $taskReplay[-1] '.agentscope/harness-context/reports/scene-01') -Directory)
    if ($taskSessions.Count -ne 2) { throw '重跑数字未创建隔离的实验记录' }
    Write-Output "全部验证通过；未调用外部模型。诊断目录：$taskTestRoot"
} finally { Pop-Location }

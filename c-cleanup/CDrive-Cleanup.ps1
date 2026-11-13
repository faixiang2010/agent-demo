<#
.SYNOPSIS
    Windows 10 C 盘保守清理与空间分析脚本。

.DESCRIPTION
    默认只读扫描，不删除任何文件。只有显式指定 -Execute、通过二次确认，
    且目标文件位于固定白名单并超过保留期限时，才会逐文件清理。

    自动清理范围仅包括：当前用户临时目录、C:\Windows\Temp，以及 Windows
    错误报告目录中的过期报告。脚本不会关闭休眠、修改虚拟内存、修改注册表、
    停止服务、结束进程、清空回收站或处理个人数据。

.PARAMETER Execute
    执行安全白名单清理。未指定时只扫描。

.PARAMETER WhatIf
    只模拟安全白名单删除，不执行实际删除；日志和报告仍会正常生成。

.PARAMETER TempMinAgeDays
    临时文件最小保留天数，默认 7 天。

.PARAMETER ReportMinAgeDays
    Windows 错误报告最小保留天数，默认 30 天。

.PARAMETER MaxScanSeconds
    C 盘只读诊断的最长时间，默认 90 秒。达到上限会生成部分扫描报告，
    且禁止执行删除。

.PARAMETER DeepScan
    对整个 C 盘做深度只读扫描。默认只扫描临时目录、用户数据目录和开发缓存
    等重点位置，日常使用不需要指定此参数。

.EXAMPLE
    .\CDrive-Cleanup.ps1
    只扫描并生成 JSON/CSV 报告。

.EXAMPLE
    .\CDrive-Cleanup.ps1 -WhatIf
    只扫描；如果同时指定 -Execute，则模拟逐文件清理但不会删除。

.EXAMPLE
    .\CDrive-Cleanup.ps1 -Execute
    扫描后要求输入 CLEAN，随后仅清理安全白名单中的过期文件。
#>

[CmdletBinding()]
param(
    [switch]$Execute,

    [switch]$WhatIf,

    [ValidateRange(1, 3650)]
    [int]$TempMinAgeDays = 7,

    [ValidateRange(1, 3650)]
    [int]$ReportMinAgeDays = 30,

    [ValidateRange(10, 3600)]
    [int]$MaxScanSeconds = 90,

    [switch]$DeepScan
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$ScriptVersion = '2.1.0-safe'
$ScriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    (Get-Location).Path
} else {
    $PSScriptRoot
}

$LogDirectory = Join-Path -Path $ScriptRoot -ChildPath 'logs'
$ReportDirectory = Join-Path -Path $ScriptRoot -ChildPath 'reports'
$RunId = Get-Date -Format 'yyyyMMdd-HHmmss'
$LogFile = Join-Path -Path $LogDirectory -ChildPath ("CDrive-Cleanup-{0}.log" -f $RunId)
$JsonReportFile = Join-Path -Path $ReportDirectory -ChildPath ("CDrive-Report-{0}.json" -f $RunId)
$CsvReportFile = Join-Path -Path $ReportDirectory -ChildPath ("CDrive-Report-{0}.csv" -f $RunId)

$script:Issues = New-Object 'System.Collections.Generic.List[object]'

function Add-Issue {
    param(
        [string]$Path,
        [string]$Reason,
        [string]$Stage = 'Scan'
    )

    $script:Issues.Add([pscustomobject]@{
        Stage  = $Stage
        Path   = $Path
        Reason = $Reason
    }) | Out-Null
}

function Ensure-OutputDirectories {
    foreach ($directory in @($LogDirectory, $ReportDirectory)) {
        if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
            [System.IO.Directory]::CreateDirectory($directory) | Out-Null
        }
    }
}

function Write-Log {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message,

        [ConsoleColor]$Color = 'Gray'
    )

    $line = '[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Write-Host $line -ForegroundColor $Color
    Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8 -ErrorAction Stop
}

function Get-NormalizedPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    try {
        $full = [System.IO.Path]::GetFullPath($Path)
        while (($full.Length -gt 3) -and ($full.EndsWith('\') -or $full.EndsWith('/'))) {
            $full = $full.Substring(0, $full.Length - 1)
        }
        return $full
    } catch {
        return $null
    }
}

function Test-PathWithinRoot {
    param(
        [Parameter(Mandatory = $true)][string]$CandidatePath,
        [Parameter(Mandatory = $true)][string]$RootPath
    )

    $candidate = Get-NormalizedPath $CandidatePath
    $root = Get-NormalizedPath $RootPath
    if ([string]::IsNullOrWhiteSpace($candidate) -or [string]::IsNullOrWhiteSpace($root)) {
        return $false
    }

    if ($candidate.Equals($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }

    return $candidate.StartsWith($root + '\', [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-ReparsePoint {
    param([Parameter(Mandatory = $true)]$Item)

    return (($Item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
}

function Test-SkippableFileAttributes {
    param([Parameter(Mandatory = $true)]$Item)

    $blocked = [System.IO.FileAttributes]::ReparsePoint -bor
               [System.IO.FileAttributes]::System -bor
               [System.IO.FileAttributes]::ReadOnly
    return (($Item.Attributes -band $blocked) -ne 0)
}

function Get-CDriveInfo {
    try {
        $driveInfo = [System.IO.DriveInfo]::new('C:\')
        if ($driveInfo.IsReady -and $driveInfo.TotalSize -gt 0) {
            return [pscustomobject]@{
                Drive       = 'C:'
                TotalBytes  = [int64]$driveInfo.TotalSize
                FreeBytes   = [int64]$driveInfo.AvailableFreeSpace
                UsedBytes   = [int64]$driveInfo.TotalSize - [int64]$driveInfo.AvailableFreeSpace
                Source      = 'System.IO.DriveInfo'
            }
        }
    } catch {
        Add-Issue -Path 'C:' -Reason $_.Exception.Message -Stage 'DriveInfo'
    }

    try {
        $disk = Get-CimInstance -ClassName Win32_LogicalDisk -Filter "DeviceID='C:'" -ErrorAction Stop
        if ($null -ne $disk -and [int64]$disk.Size -gt 0) {
            return [pscustomobject]@{
                Drive       = 'C:'
                TotalBytes  = [int64]$disk.Size
                FreeBytes   = [int64]$disk.FreeSpace
                UsedBytes   = [int64]$disk.Size - [int64]$disk.FreeSpace
                Source      = 'Win32_LogicalDisk'
            }
        }
    } catch {
        Add-Issue -Path 'C:' -Reason $_.Exception.Message -Stage 'DriveInfo'
    }

    try {
        $drive = Get-PSDrive -Name C -ErrorAction Stop
        if ($null -eq $drive.Free -or $null -eq $drive.Used) {
            throw 'PSDrive C 未返回有效的 Used/Free 数值'
        }
        return [pscustomobject]@{
            Drive       = 'C:'
            TotalBytes  = [int64]($drive.Used + $drive.Free)
            FreeBytes   = [int64]$drive.Free
            UsedBytes   = [int64]$drive.Used
            Source      = 'PSDrive'
        }
    } catch {
        Add-Issue -Path 'C:' -Reason $_.Exception.Message -Stage 'DriveInfo'
        return [pscustomobject]@{
            Drive       = 'C:'
            TotalBytes  = 0
            FreeBytes   = 0
            UsedBytes   = 0
            Source      = 'Unavailable'
        }
    }
}

function Format-GB {
    param([int64]$Bytes)

    return ('{0:N2} GB' -f ($Bytes / 1GB))
}

function Get-ByteSum {
    param([array]$Items)

    [int64]$sum = 0
    foreach ($item in @($Items)) {
        if ($null -ne $item -and $null -ne $item.Bytes) {
            $sum += [int64]$item.Bytes
        }
    }
    return $sum
}

function Get-ObjectCount {
    param($Value)

    if ($null -eq $Value) {
        return 0
    }
    if ($Value -is [System.Array]) {
        return $Value.Length
    }
    if ($Value -is [System.Collections.ICollection]) {
        return $Value.Count
    }
    return 1
}

function New-SafeTargetSpecs {
    $profile = $env:USERPROFILE
    $localAppData = $env:LOCALAPPDATA
    if ([string]::IsNullOrWhiteSpace($localAppData) -and -not [string]::IsNullOrWhiteSpace($profile)) {
        $localAppData = Join-Path -Path $profile -ChildPath 'AppData\Local'
    }

    return @(
        [pscustomobject]@{
            Root       = (Join-Path -Path $localAppData -ChildPath 'Temp')
            Category   = 'UserTemp'
            Label      = '当前用户临时文件'
            MinAgeDays = $TempMinAgeDays
        },
        [pscustomobject]@{
            Root       = 'C:\Windows\Temp'
            Category   = 'WindowsTemp'
            Label      = 'Windows 临时文件'
            MinAgeDays = $TempMinAgeDays
        },
        [pscustomobject]@{
            Root       = (Join-Path -Path $localAppData -ChildPath 'Microsoft\Windows\WER')
            Category   = 'UserWer'
            Label      = '当前用户 Windows 错误报告'
            MinAgeDays = $ReportMinAgeDays
        },
        [pscustomobject]@{
            Root       = 'C:\ProgramData\Microsoft\Windows\WER'
            Category   = 'SystemWer'
            Label      = '系统 Windows 错误报告'
            MinAgeDays = $ReportMinAgeDays
        }
    )
}

function New-ManualReviewSpecs {
    $profile = $env:USERPROFILE
    $targets = @(
        [pscustomobject]@{ Path = 'C:\hiberfil.sys'; Kind = 'File'; Category = 'SystemFile'; Label = '休眠文件，仅报告'; QuickScan = $true },
        [pscustomobject]@{ Path = 'C:\pagefile.sys'; Kind = 'File'; Category = 'SystemFile'; Label = '虚拟内存文件，仅报告'; QuickScan = $true },
        [pscustomobject]@{ Path = 'C:\swapfile.sys'; Kind = 'File'; Category = 'SystemFile'; Label = '系统交换文件，仅报告'; QuickScan = $true },
        [pscustomobject]@{ Path = 'C:\Windows\SoftwareDistribution'; Kind = 'Directory'; Category = 'WindowsUpdate'; Label = 'Windows Update 数据，仅报告'; QuickScan = $false },
        [pscustomobject]@{ Path = 'C:\Windows\Installer'; Kind = 'Directory'; Category = 'WindowsInstaller'; Label = 'Windows Installer 缓存，仅报告'; QuickScan = $false },
        [pscustomobject]@{ Path = 'C:\Windows\WinSxS'; Kind = 'Directory'; Category = 'ComponentStore'; Label = 'Windows 组件存储，仅报告'; QuickScan = $false },
        [pscustomobject]@{ Path = 'C:\Windows\Prefetch'; Kind = 'Directory'; Category = 'Prefetch'; Label = 'Prefetch，仅报告'; QuickScan = $false },
        [pscustomobject]@{ Path = 'C:\Windows.old'; Kind = 'Directory'; Category = 'PreviousWindows'; Label = '旧 Windows 安装，仅报告'; QuickScan = $false },
        [pscustomobject]@{ Path = 'C:\$Recycle.Bin'; Kind = 'Directory'; Category = 'RecycleBin'; Label = '回收站，仅报告'; QuickScan = $false },
        [pscustomobject]@{ Path = 'C:\Program Files'; Kind = 'Directory'; Category = 'InstalledApps'; Label = '64 位程序，仅报告'; QuickScan = $false },
        [pscustomobject]@{ Path = 'C:\Program Files (x86)'; Kind = 'Directory'; Category = 'InstalledApps'; Label = '32 位程序，仅报告'; QuickScan = $false }
    )

    if (-not [string]::IsNullOrWhiteSpace($profile)) {
        $targets += @(
            [pscustomobject]@{ Path = (Join-Path $profile 'Downloads'); Kind = 'Directory'; Category = 'UserData'; Label = '下载目录，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'Desktop'); Kind = 'Directory'; Category = 'UserData'; Label = '桌面，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'Documents'); Kind = 'Directory'; Category = 'UserData'; Label = '文档，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'AppData\Local\npm-cache'); Kind = 'Directory'; Category = 'DeveloperCache'; Label = 'npm 缓存，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'AppData\Local\pnpm'); Kind = 'Directory'; Category = 'DeveloperCache'; Label = 'pnpm store，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'AppData\Local\pnpm-cache'); Kind = 'Directory'; Category = 'DeveloperCache'; Label = 'pnpm 缓存，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'AppData\Roaming\Tencent\WXWork'); Kind = 'Directory'; Category = 'PersonalAppData'; Label = '企业微信数据，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'AppData\Roaming\Tencent\xwechat'); Kind = 'Directory'; Category = 'PersonalAppData'; Label = '微信 xwechat 数据，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile 'AppData\Roaming\Tencent\WeChat'); Kind = 'Directory'; Category = 'PersonalAppData'; Label = '微信 WeChat 数据，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile '.cache\codex-runtimes'); Kind = 'Directory'; Category = 'DeveloperCache'; Label = 'Codex 运行时缓存，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile '.lingma'); Kind = 'Directory'; Category = 'DeveloperCache'; Label = '通义灵码数据，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile '.trae-cn'); Kind = 'Directory'; Category = 'DeveloperCache'; Label = 'Trae 数据，仅报告'; QuickScan = $false },
            [pscustomobject]@{ Path = (Join-Path $profile '.ollaya'); Kind = 'Directory'; Category = 'ModelData'; Label = '模型数据，仅报告'; QuickScan = $false }
        )
    }

    return $targets
}

function Get-Inventory {
    param(
        [Parameter(Mandatory = $true)][array]$SafeSpecs,
        [Parameter(Mandatory = $true)][array]$ManualSpecs,
        [Parameter(Mandatory = $true)][int]$ScanLimitSeconds,
        [switch]$DeepScan
    )

    $scanTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $script:InventoryTimedOut = $false
    $topDirectoryBytes = @{}
    $manualBytes = @{}
    $manualScanned = @{}
    $explicitManualQueued = @{}
    $largeFiles = New-Object 'System.Collections.Generic.List[object]'
    $safeCandidates = New-Object 'System.Collections.Generic.List[object]'
    $stack = New-Object System.Collections.Stack
    $largeFileThreshold = 512MB
    $nowUtc = (Get-Date).ToUniversalTime()

    function Test-ScanDeadline {
        if ($scanTimer.Elapsed.TotalSeconds -ge $ScanLimitSeconds) {
            if (-not $script:InventoryTimedOut) {
                $script:InventoryTimedOut = $true
                Add-Issue -Path 'C:\' -Reason ("只读扫描达到 {0} 秒上限，后续目录未扫描" -f $ScanLimitSeconds) -Stage 'Inventory'
            }
            return $true
        }
        return $false
    }

    foreach ($manual in $ManualSpecs) {
        $manualBytes[$manual.Path] = [int64]0
        $manualScanned[$manual.Path] = $false
    }

    function Add-LargeFile {
        param([Parameter(Mandatory = $true)]$Item)

        $record = [pscustomobject]@{
            Path             = $Item.FullName
            Bytes            = [int64]$Item.Length
            SizeGB           = [math]::Round(([int64]$Item.Length) / 1GB, 2)
            LastWriteTimeUtc = $Item.LastWriteTimeUtc
        }

        if ($largeFiles.Count -lt 100) {
            $largeFiles.Add($record) | Out-Null
            return
        }

        $smallest = $largeFiles | Sort-Object -Property Bytes | Select-Object -First 1
        if ($null -ne $smallest -and $record.Bytes -gt $smallest.Bytes) {
            $index = $largeFiles.IndexOf($smallest)
            if ($index -ge 0) {
                $largeFiles[$index] = $record
            }
        }
    }

    function Process-File {
        param(
            [Parameter(Mandatory = $true)]$Item,
            [Parameter(Mandatory = $true)][string]$TopName
        )

        if (Test-ReparsePoint $Item) {
            Add-Issue -Path $Item.FullName -Reason '跳过重解析点/符号链接' -Stage 'Inventory'
            return
        }

        $bytes = [int64]$Item.Length
        if (-not $topDirectoryBytes.ContainsKey($TopName)) {
            $topDirectoryBytes[$TopName] = [int64]0
        }
        $topDirectoryBytes[$TopName] = [int64]$topDirectoryBytes[$TopName] + $bytes

        if ($bytes -ge $largeFileThreshold) {
            Add-LargeFile $Item | Out-Null
        }

        $normalizedFile = Get-NormalizedPath $Item.FullName
        foreach ($manual in $ManualSpecs) {
            $isMatch = if ($manual.Kind -eq 'File') {
                $normalizedManual = Get-NormalizedPath $manual.Path
                $normalizedFile.Equals($normalizedManual, [System.StringComparison]::OrdinalIgnoreCase)
            } else {
                Test-PathWithinRoot -CandidatePath $normalizedFile -RootPath $manual.Path
            }

            if ($isMatch) {
                $manualBytes[$manual.Path] = [int64]$manualBytes[$manual.Path] + $bytes
                if ($manual.Kind -eq 'File') {
                    $manualScanned[$manual.Path] = $true
                }
            }
        }

        foreach ($safe in $SafeSpecs) {
            if (-not (Test-PathWithinRoot -CandidatePath $normalizedFile -RootPath $safe.Root)) {
                continue
            }

            $ageDays = ($nowUtc - $Item.LastWriteTimeUtc).TotalDays
            if ($ageDays -lt $safe.MinAgeDays) {
                continue
            }

            $safeCandidates.Add([pscustomobject]@{
                Path             = $Item.FullName
                Root             = $safe.Root
                Category         = $safe.Category
                Label            = $safe.Label
                Bytes            = $bytes
                SizeGB           = [math]::Round($bytes / 1GB, 4)
                LastWriteTimeUtc = $Item.LastWriteTimeUtc
                AgeDays          = [math]::Round($ageDays, 1)
                Result           = 'Candidate'
                ResultMessage    = ''
                FreedBytes       = [int64]0
            }) | Out-Null
        }
    }

    if ($DeepScan) {
        try {
            $rootPriority = @{
                'C:\Users'              = 0
                'C:\ProgramData'        = 1
                'C:\Program Files'      = 2
                'C:\Program Files (x86)'= 3
                'C:\Windows'            = 4
            }
            $rootItems = @(Get-ChildItem -LiteralPath 'C:\' -Force -ErrorAction Stop | Sort-Object -Property @(
                @{ Expression = { if ($rootPriority.ContainsKey($_.FullName)) { $rootPriority[$_.FullName] } else { 10 } }; Descending = $true },
                @{ Expression = { $_.Name } }
            ))
        } catch {
            Add-Issue -Path 'C:\' -Reason $_.Exception.Message -Stage 'Inventory'
            $rootItems = @()
        }

        foreach ($item in $rootItems) {
            if (Test-ScanDeadline) {
                break
            }

            if (Test-ReparsePoint $item) {
                Add-Issue -Path $item.FullName -Reason '跳过重解析点/符号链接' -Stage 'Inventory'
                continue
            }

            if ($item.PSIsContainer) {
                $topName = $item.FullName
                if (-not $topDirectoryBytes.ContainsKey($topName)) {
                    $topDirectoryBytes[$topName] = [int64]0
                }
                $stack.Push([pscustomobject]@{ Path = $item.FullName; TopName = $topName }) | Out-Null
            } else {
                try {
                    Process-File -Item $item -TopName '[C:\根目录文件]' | Out-Null
                } catch {
                    Add-Issue -Path $item.FullName -Reason $_.Exception.Message -Stage 'Inventory'
                }
            }
        }
    } else {
        try {
            $rootFiles = @(Get-ChildItem -LiteralPath 'C:\' -Force -File -ErrorAction Stop)
            foreach ($rootFile in $rootFiles) {
                if (Test-ScanDeadline) {
                    break
                }
                try {
                    Process-File -Item $rootFile -TopName '[C:\根目录文件]' | Out-Null
                } catch {
                    Add-Issue -Path $rootFile.FullName -Reason $_.Exception.Message -Stage 'Inventory'
                }
            }
        } catch {
            Add-Issue -Path 'C:\' -Reason $_.Exception.Message -Stage 'QuickInventory'
        }

        $quickRoots = New-Object 'System.Collections.Generic.List[string]'
        $quickRootSeen = @{}
        foreach ($safe in $SafeSpecs) {
            if (-not $quickRootSeen.ContainsKey($safe.Root)) {
                $quickRootSeen[$safe.Root] = $true
                $quickRoots.Add($safe.Root) | Out-Null
            }
        }
        foreach ($manual in $ManualSpecs) {
            if ($manual.Kind -eq 'Directory' -and $manual.QuickScan -and -not $quickRootSeen.ContainsKey($manual.Path)) {
                $quickRootSeen[$manual.Path] = $true
                $quickRoots.Add($manual.Path) | Out-Null
            }
        }

        foreach ($quickRoot in $quickRoots.ToArray()) {
            if (Test-ScanDeadline) {
                break
            }
            try {
                $quickRootItem = Get-Item -LiteralPath $quickRoot -Force -ErrorAction Stop
                if (Test-ReparsePoint $quickRootItem) {
                    Add-Issue -Path $quickRoot -Reason '跳过重解析点/符号链接根目录' -Stage 'QuickInventory'
                    continue
                }
                if ($quickRootItem.PSIsContainer) {
                    $topDirectoryBytes[$quickRootItem.FullName] = [int64]0
                    $stack.Push([pscustomobject]@{ Path = $quickRootItem.FullName; TopName = $quickRootItem.FullName }) | Out-Null
                } else {
                    Process-File -Item $quickRootItem -TopName '[C:\根目录文件]' | Out-Null
                }
            } catch {
                Add-Issue -Path $quickRoot -Reason $_.Exception.Message -Stage 'QuickInventory'
            }
        }
    }

    while ($stack.Count -gt 0) {
        if (Test-ScanDeadline) {
            break
        }

        $node = $stack.Pop()
        try {
            $children = @(Get-ChildItem -LiteralPath $node.Path -Force -ErrorAction Stop)
        } catch {
            Add-Issue -Path $node.Path -Reason $_.Exception.Message -Stage 'Inventory'

            foreach ($manual in $ManualSpecs) {
                if ($manual.Kind -ne 'Directory' -or $explicitManualQueued.ContainsKey($manual.Path)) {
                    continue
                }

                $manualPath = Get-NormalizedPath $manual.Path
                $blockedNode = Get-NormalizedPath $node.Path
                if ([string]::IsNullOrWhiteSpace($manualPath) -or [string]::IsNullOrWhiteSpace($blockedNode)) {
                    continue
                }
                if (-not $manualPath.StartsWith($blockedNode + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
                    continue
                }
                if (-not (Test-Path -LiteralPath $manual.Path -PathType Container)) {
                    continue
                }

                $explicitManualQueued[$manual.Path] = $true
                $stack.Push([pscustomobject]@{ Path = $manual.Path; TopName = $node.TopName }) | Out-Null
                Add-Issue -Path $manual.Path -Reason '父目录无法访问，改为直接扫描已知子目录' -Stage 'InventoryFallback'
            }
            continue
        }

        foreach ($manual in $ManualSpecs) {
            if ($manual.Kind -eq 'Directory' -and
                (Get-NormalizedPath $manual.Path).Equals((Get-NormalizedPath $node.Path), [System.StringComparison]::OrdinalIgnoreCase)) {
                $manualScanned[$manual.Path] = $true
            }
        }

        foreach ($child in $children) {
            if (Test-ScanDeadline) {
                break
            }

            if (Test-ReparsePoint $child) {
                Add-Issue -Path $child.FullName -Reason '跳过重解析点/符号链接' -Stage 'Inventory'
                continue
            }

            if ($child.PSIsContainer) {
                $stack.Push([pscustomobject]@{ Path = $child.FullName; TopName = $node.TopName }) | Out-Null
            } else {
                try {
                    Process-File -Item $child -TopName $node.TopName | Out-Null
                } catch {
                    Add-Issue -Path $child.FullName -Reason $_.Exception.Message -Stage 'Inventory'
                }
            }
        }
    }

    return [pscustomobject]@{
        TopDirectoryBytes = $topDirectoryBytes
        ManualBytes       = $manualBytes
        ManualScanned     = $manualScanned
        LargeFiles        = @($largeFiles | Sort-Object -Property Bytes -Descending)
        SafeCandidates    = @($safeCandidates | Sort-Object -Property Bytes -Descending)
        ScanMode          = if ($DeepScan) { 'Deep' } else { 'Quick' }
        TimedOut          = [bool]$script:InventoryTimedOut
        ElapsedSeconds    = [math]::Round($scanTimer.Elapsed.TotalSeconds, 1)
    }
}

function Get-ManualReport {
    param(
        [Parameter(Mandatory = $true)][array]$ManualSpecs,
        [Parameter(Mandatory = $true)]$ManualBytes,
        [Parameter(Mandatory = $true)]$ManualScanned
    )

    $rows = New-Object 'System.Collections.Generic.List[object]'
    foreach ($spec in $ManualSpecs) {
        $exists = Test-Path -LiteralPath $spec.Path
        $manualValue = $null
        if ($ManualBytes -is [System.Collections.IDictionary]) {
            $manualValue = $ManualBytes[$spec.Path]
        } else {
            $manualTable = @($ManualBytes | Where-Object { $_ -is [System.Collections.IDictionary] } | Select-Object -First 1)
            if ($manualTable.Count -eq 1) {
                $manualValue = $manualTable[0][$spec.Path]
            }
        }
        $bytes = if ($null -eq $manualValue) { [int64]0 } else { [int64]$manualValue }
        if ($spec.Kind -eq 'File' -and $exists) {
            try {
                $bytes = [int64](Get-Item -LiteralPath $spec.Path -Force -ErrorAction Stop).Length
            } catch {
                Add-Issue -Path $spec.Path -Reason $_.Exception.Message -Stage 'ManualReport'
            }
        }

        $scannedValue = $false
        if ($ManualScanned -is [System.Collections.IDictionary]) {
            $scannedValue = [bool]$ManualScanned[$spec.Path]
        }
        $scanStatus = if ($scannedValue) { 'Scanned' } else { 'NotScanned' }
        $recommendation = if (-not $scannedValue) {
            '本次扫描未实际读取该目录；使用 -DeepScan 或延长扫描时间后重试，脚本仍不会删除'
        } else {
            '仅人工确认；脚本不会删除'
        }

        $rows.Add([pscustomobject]@{
            Path       = $spec.Path
            Category   = $spec.Category
            Label      = $spec.Label
            Exists     = $exists
            SizeGB     = [math]::Round($bytes / 1GB, 2)
            Automated  = $false
            ScanStatus = $scanStatus
            Recommendation = $recommendation
        }) | Out-Null
    }
    return $rows.ToArray()
}

function Invoke-SafeCleanup {
    param(
        [Parameter(Mandatory = $true)][array]$Candidates,
        [Parameter(Mandatory = $true)][array]$SafeSpecs
    )

    $resultCounts = @{
        Deleted   = 0
        WhatIf    = 0
        Skipped   = 0
        Failed    = 0
        NotFound  = 0
    }

    foreach ($candidate in $Candidates) {
        try {
            if (-not (Test-Path -LiteralPath $candidate.Path -PathType Leaf)) {
                $candidate.Result = 'NotFound'
                $candidate.ResultMessage = '文件不存在或已被其他程序移除'
                $resultCounts.NotFound++
                Write-Log ("跳过不存在文件: {0}" -f $candidate.Path) 'DarkGray'
                continue
            }

            $item = Get-Item -LiteralPath $candidate.Path -Force -ErrorAction Stop
            if ($item.PSIsContainer) {
                $candidate.Result = 'Skipped'
                $candidate.ResultMessage = '目标不是文件'
                $resultCounts.Skipped++
                continue
            }

            $matchingSpec = @($SafeSpecs | Where-Object {
                $_.Category -eq $candidate.Category -and
                $_.Root.Equals($candidate.Root, [System.StringComparison]::OrdinalIgnoreCase)
            })
            if ($matchingSpec.Count -ne 1) {
                $candidate.Result = 'Skipped'
                $candidate.ResultMessage = '安全目标规格校验失败'
                $resultCounts.Skipped++
                Add-Issue -Path $item.FullName -Reason '删除前安全目标规格校验失败' -Stage 'Delete'
                Write-Log ("安全拦截未知目标: {0}" -f $item.FullName) 'Red'
                continue
            }

            $rootItem = Get-Item -LiteralPath $candidate.Root -Force -ErrorAction Stop
            if (-not $rootItem.PSIsContainer -or (Test-ReparsePoint $rootItem)) {
                $candidate.Result = 'Skipped'
                $candidate.ResultMessage = '白名单根目录不是普通目录或是重解析点'
                $resultCounts.Skipped++
                Add-Issue -Path $candidate.Root -Reason '删除前白名单根目录校验失败' -Stage 'Delete'
                Write-Log ("安全拦截异常根目录: {0}" -f $candidate.Root) 'Red'
                continue
            }

            if (Test-ReparsePoint $item) {
                $candidate.Result = 'Skipped'
                $candidate.ResultMessage = '重解析点/符号链接'
                $resultCounts.Skipped++
                Write-Log ("跳过重解析点: {0}" -f $candidate.Path) 'DarkYellow'
                continue
            }

            if (Test-SkippableFileAttributes $item) {
                $candidate.Result = 'Skipped'
                $candidate.ResultMessage = '系统、只读或重解析属性'
                $resultCounts.Skipped++
                Write-Log ("跳过特殊属性文件: {0}" -f $candidate.Path) 'DarkYellow'
                continue
            }

            if (-not (Test-PathWithinRoot -CandidatePath $item.FullName -RootPath $candidate.Root)) {
                $candidate.Result = 'Skipped'
                $candidate.ResultMessage = '路径不在白名单根目录内'
                $resultCounts.Skipped++
                Add-Issue -Path $item.FullName -Reason '删除前路径白名单校验失败' -Stage 'Delete'
                Write-Log ("安全拦截越界路径: {0}" -f $item.FullName) 'Red'
                continue
            }

            $ageDays = ((Get-Date).ToUniversalTime() - $item.LastWriteTimeUtc).TotalDays
            $requiredAge = if ($candidate.Category -like '*Wer') { $ReportMinAgeDays } else { $TempMinAgeDays }
            if ($ageDays -lt $requiredAge) {
                $candidate.Result = 'Skipped'
                $candidate.ResultMessage = '重新检查后未达到最小保留天数'
                $resultCounts.Skipped++
                continue
            }

            if ($WhatIf) {
                $candidate.Result = 'WhatIf'
                $candidate.ResultMessage = '模拟模式，未删除'
                $resultCounts.WhatIf++
                Write-Log ("模拟删除: {0} ({1})" -f $candidate.Path, (Format-GB $candidate.Bytes)) 'Cyan'
                continue
            }

            Remove-Item -LiteralPath $item.FullName -Force -ErrorAction Stop
            $candidate.Result = 'Deleted'
            $candidate.ResultMessage = '已删除'
            $candidate.FreedBytes = [int64]$item.Length
            $resultCounts.Deleted++
            Write-Log ("删除文件: {0} ({1})" -f $candidate.Path, (Format-GB $candidate.FreedBytes)) 'Green'
        } catch {
            $candidate.Result = 'Failed'
            $candidate.ResultMessage = $_.Exception.Message
            $resultCounts.Failed++
            Add-Issue -Path $candidate.Path -Reason $_.Exception.Message -Stage 'Delete'
            Write-Log ("删除失败，已跳过: {0} | {1}" -f $candidate.Path, $_.Exception.Message) 'DarkYellow'
        }
    }

    return [pscustomobject]$resultCounts
}

function Write-Reports {
    param(
        [Parameter(Mandatory = $true)]$DriveBefore,
        [Parameter(Mandatory = $true)]$DriveAfter,
        [Parameter(Mandatory = $true)]$Inventory,
        [Parameter(Mandatory = $true)][array]$ManualReport,
        [Parameter(Mandatory = $true)][string]$ExecutionMode,
        [Parameter(Mandatory = $true)]$CleanupResult
    )

    $topDirectoryRows = New-Object 'System.Collections.Generic.List[object]'
    foreach ($entry in $Inventory.TopDirectoryBytes.GetEnumerator()) {
        $topDirectoryRows.Add([pscustomobject]@{
            Path   = $entry.Key
            SizeGB = [math]::Round(([int64]$entry.Value) / 1GB, 2)
        }) | Out-Null
    }
    $topDirectories = $topDirectoryRows.ToArray() | Sort-Object -Property SizeGB -Descending

    $report = [ordered]@{
        SchemaVersion       = '1.0'
        ScriptVersion       = $ScriptVersion
        RunId                = $RunId
        StartedAt            = $script:RunStarted.ToString('o')
        FinishedAt           = (Get-Date).ToString('o')
        ExecutionMode        = $ExecutionMode
        ScanMode             = $Inventory.ScanMode
        WhatIf               = [bool]$WhatIf
        TempMinAgeDays       = $TempMinAgeDays
        ReportMinAgeDays     = $ReportMinAgeDays
        MaxScanSeconds       = $MaxScanSeconds
        InventoryComplete    = (-not $Inventory.TimedOut)
        InventoryElapsedSec  = $Inventory.ElapsedSeconds
        DriveBefore          = $DriveBefore
        DriveAfter           = $DriveAfter
        SafeCandidateCount   = Get-ObjectCount $Inventory.SafeCandidates
        SafeCandidateGB      = [math]::Round(((Get-ByteSum -Items $Inventory.SafeCandidates) / 1GB), 2)
        TopDirectories       = $topDirectories
        LargeFiles           = $Inventory.LargeFiles
        SafeCandidates       = $Inventory.SafeCandidates
        ManualReview         = $ManualReport
        Issues               = $script:Issues.ToArray()
        CleanupResult        = $CleanupResult
    }

    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $JsonReportFile -Encoding UTF8 -Force

    $csvRows = New-Object 'System.Collections.Generic.List[object]'
    foreach ($candidate in $Inventory.SafeCandidates) {
        $csvRows.Add([pscustomobject]@{
            Type             = 'SafeCandidate'
            Category         = $candidate.Category
            Label            = $candidate.Label
            Path             = $candidate.Path
            SizeGB           = $candidate.SizeGB
            LastWriteTimeUtc = $candidate.LastWriteTimeUtc
            Exists           = (Test-Path -LiteralPath $candidate.Path)
            Result           = $candidate.Result
            ResultMessage    = $candidate.ResultMessage
        }) | Out-Null
    }
    foreach ($manual in $ManualReport) {
        $csvRows.Add([pscustomobject]@{
            Type             = 'ManualReview'
            Category         = $manual.Category
            Label            = $manual.Label
            Path             = $manual.Path
            SizeGB           = $manual.SizeGB
            LastWriteTimeUtc = ''
            Exists           = $manual.Exists
            Result           = 'ReportOnly'
            ResultMessage    = $manual.Recommendation
        }) | Out-Null
    }
    if ($csvRows.Count -gt 0) {
        $csvRows | Export-Csv -LiteralPath $CsvReportFile -NoTypeInformation -Encoding UTF8 -Force
    } else {
        'Type,Category,Label,Path,SizeGB,LastWriteTimeUtc,Exists,Result,ResultMessage' |
            Set-Content -LiteralPath $CsvReportFile -Encoding UTF8 -Force
    }
}

Ensure-OutputDirectories
$script:RunStarted = Get-Date
Set-Content -LiteralPath $LogFile -Value '' -Encoding UTF8 -Force -ErrorAction Stop

$safeSpecs = New-SafeTargetSpecs
$manualSpecs = New-ManualReviewSpecs
$scanModeLabel = if ($DeepScan) { 'Deep' } else { 'Quick' }
$executionMode = if ($Execute) { "Execute-$scanModeLabel" } else { "ScanOnly-$scanModeLabel" }
if ($WhatIf) {
    $executionMode = if ($Execute) { "Execute-$scanModeLabel-WhatIf" } else { "ScanOnly-$scanModeLabel-WhatIf" }
}

Write-Log ("C 盘安全清理开始 | 版本={0} | 模式={1}" -f $ScriptVersion, $executionMode) 'Magenta'
Write-Log ("安全策略: 临时文件保留 {0} 天，错误报告保留 {1} 天；不处理个人数据和系统配置" -f $TempMinAgeDays, $ReportMinAgeDays) 'Cyan'

$driveBefore = Get-CDriveInfo
Write-Log ("清理前 C 盘剩余: {0}" -f (Format-GB $driveBefore.FreeBytes)) 'Cyan'
Write-Host '正在只读扫描 C 盘，可能需要一些时间；不会在扫描阶段删除文件。' -ForegroundColor Yellow

$inventoryOutput = @(Get-Inventory -SafeSpecs $safeSpecs -ManualSpecs $manualSpecs -ScanLimitSeconds $MaxScanSeconds -DeepScan:$DeepScan)
$inventory = @($inventoryOutput | Where-Object {
    $null -ne $_.PSObject.Properties['TopDirectoryBytes'] -and
    $null -ne $_.PSObject.Properties['SafeCandidates']
} | Select-Object -First 1)
if ($inventory.Count -ne 1) {
    throw '扫描结果对象无效，出于安全原因停止，不执行清理。'
}
$inventory = $inventory[0]
$manualReport = Get-ManualReport -ManualSpecs $manualSpecs -ManualBytes $inventory.ManualBytes -ManualScanned $inventory.ManualScanned
$initialCleanupResult = [pscustomobject]@{ Deleted = 0; WhatIf = 0; Skipped = 0; Failed = 0; NotFound = 0 }

Write-Host ''
Write-Host '==================== 扫描结果 ====================' -ForegroundColor Magenta
Write-Host ("C 盘剩余: {0}" -f (Format-GB $driveBefore.FreeBytes)) -ForegroundColor Cyan
Write-Host ("安全白名单候选文件: {0} 个，约 {1}" -f (Get-ObjectCount $inventory.SafeCandidates), (Format-GB (Get-ByteSum -Items $inventory.SafeCandidates))) -ForegroundColor Green
Write-Host ("大文件候选: {0} 个（单文件不小于 512 MB）" -f (Get-ObjectCount $inventory.LargeFiles)) -ForegroundColor Yellow
$manualExistingCount = 0
foreach ($manualItem in $manualReport) {
    if ($manualItem.Exists) {
        $manualExistingCount++
    }
}
Write-Host ("人工复核项目: {0} 个；脚本不会自动处理" -f $manualExistingCount) -ForegroundColor Yellow
Write-Host ("扫描模式: {0}（使用 -DeepScan 才会枚举整个 C 盘）" -f $inventory.ScanMode) -ForegroundColor Cyan
Write-Host ("扫描耗时: {0} 秒；扫描完整: {1}" -f $inventory.ElapsedSeconds, (-not $inventory.TimedOut)) -ForegroundColor $(if ($inventory.TimedOut) { 'DarkYellow' } else { 'Green' })
if ($script:Issues.Count -gt 0) {
    Write-Host ("扫描记录了 {0} 个跳过/访问问题，详情见报告。" -f $script:Issues.Count) -ForegroundColor DarkYellow
}

if ($Execute -and $inventory.TimedOut) {
    $executionMode = 'ScanTimedOut-NoDelete'
    Write-Log '只读扫描未完整完成，出于安全原因禁止执行删除；请提高 -MaxScanSeconds 后重新扫描。' 'Red'
} elseif ($Execute) {
    if ($WhatIf) {
        Write-Log '已指定 -Execute -WhatIf：开始模拟，不删除文件。' 'Cyan'
    } else {
        Write-Host ''
        Write-Host '安全确认：只会处理白名单中的过期临时文件和错误报告；不会处理个人数据、回收站或系统配置。' -ForegroundColor Yellow
        $confirmation = Read-Host '如确认执行，请准确输入 CLEAN；其他输入均取消'
        if ($confirmation -cne 'CLEAN') {
            Write-Log '用户未输入 CLEAN，已取消；未删除任何文件。' 'Magenta'
            $executionMode = 'Cancelled'
        } elseif ((Get-ObjectCount $inventory.SafeCandidates) -eq 0) {
            Write-Log '没有符合条件的安全候选文件，未执行删除。' 'Green'
        } else {
            Write-Log ("开始逐文件清理，共 {0} 个候选文件。" -f (Get-ObjectCount $inventory.SafeCandidates)) 'Yellow'
            $initialCleanupResult = Invoke-SafeCleanup -Candidates $inventory.SafeCandidates -SafeSpecs $safeSpecs
        }
    }
}

$driveAfter = Get-CDriveInfo
Write-Log ("清理后 C 盘剩余: {0}" -f (Format-GB $driveAfter.FreeBytes)) 'Green'
Write-Log ("实际空间变化: {0}" -f (Format-GB ($driveAfter.FreeBytes - $driveBefore.FreeBytes))) 'Magenta'
Write-Reports -DriveBefore $driveBefore -DriveAfter $driveAfter -Inventory $inventory -ManualReport $manualReport -ExecutionMode $executionMode -CleanupResult $initialCleanupResult
Write-Log ("JSON 报告: {0}" -f $JsonReportFile) 'DarkGray'
Write-Log ("CSV 报告: {0}" -f $CsvReportFile) 'DarkGray'
Write-Log 'C 盘安全清理结束。' 'Magenta'

Write-Host ''
Write-Host '==================== 处理完成 ====================' -ForegroundColor Magenta
Write-Host ("JSON 报告: {0}" -f $JsonReportFile) -ForegroundColor DarkGray
Write-Host ("CSV 报告:  {0}" -f $CsvReportFile) -ForegroundColor DarkGray
Write-Host ("日志:      {0}" -f $LogFile) -ForegroundColor DarkGray

param(
    [ValidateSet('Recall', 'Interactive', 'Auto')]
    [string]$Mode = 'Recall'
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOldJavaHome = $env:JAVA_HOME
$taskOldMavenOpts = $env:MAVEN_OPTS
$taskOldInputEncoding = [Console]::InputEncoding
$taskOldOutputEncoding = [Console]::OutputEncoding
$taskUtf8 = [System.Text.UTF8Encoding]::new($false)

Push-Location $taskRoot
try {
    # Set Windows console input/output and PowerShell native-process pipe encoding.
    [Console]::InputEncoding = $taskUtf8
    [Console]::OutputEncoding = $taskUtf8
    $OutputEncoding = $taskUtf8
    $env:JAVA_HOME = 'D:\GreenSoft\java\jdk-17.0.14'
    # exec:java runs inside Maven's JVM. JVM encoding must be set before Maven starts.
    $env:MAVEN_OPTS = "$taskOldMavenOpts -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
    $taskMavenArgs = @('compile', 'exec:java',
        '-Dexec.mainClass=com.example.agentscope.harness.memory.MemoryDemo')
    if ($Mode -eq 'Recall') { $taskMavenArgs += '-Dexec.args=--recall' }
    if ($Mode -eq 'Auto') { $taskMavenArgs += '-Dexec.args=--auto' }
    & 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' @taskMavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $taskOldJavaHome
    $env:MAVEN_OPTS = $taskOldMavenOpts
    [Console]::InputEncoding = $taskOldInputEncoding
    [Console]::OutputEncoding = $taskOldOutputEncoding
    Pop-Location
}

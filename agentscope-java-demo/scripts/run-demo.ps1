param(
    [string]$MainClass = 'com.example.agentscope.FirstAgent',
    [string]$DemoArgs = ''
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
    [Console]::InputEncoding = $taskUtf8
    [Console]::OutputEncoding = $taskUtf8
    $OutputEncoding = $taskUtf8
    $env:JAVA_HOME = 'D:\GreenSoft\java\jdk-17.0.14'
    # exec:java shares Maven's JVM. Set JVM encoding before launching Maven.
    $env:MAVEN_OPTS = "$taskOldMavenOpts -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
    $taskMavenArgs = @('compile', 'exec:java', "-Dexec.mainClass=$MainClass")
    if ($DemoArgs) { $taskMavenArgs += "-Dexec.args=$DemoArgs" }
    & 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' @taskMavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $taskOldJavaHome
    $env:MAVEN_OPTS = $taskOldMavenOpts
    [Console]::InputEncoding = $taskOldInputEncoding
    [Console]::OutputEncoding = $taskOldOutputEncoding
    Pop-Location
}

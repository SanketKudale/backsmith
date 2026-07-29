@echo off
setlocal
set "ROOT=%~dp0"
set "MVN_HOME=%ROOT%.mvn\apache-maven-3.9.11"
if not exist "%MVN_HOME%\bin\mvn.cmd" (
  echo Downloading Apache Maven 3.9.11...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:TEMP ('backsmith-maven-' + [guid]::NewGuid() + '.zip'); try { Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile $zip; Expand-Archive -Force $zip '%ROOT%.mvn' } finally { Remove-Item -ErrorAction SilentlyContinue $zip }"
  if errorlevel 1 exit /b 1
)
call "%MVN_HOME%\bin\mvn.cmd" %*

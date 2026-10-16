@echo off
setlocal
set VERSION=8.11.1
if "%ICONFORGE_USE_SYSTEM_GRADLE%"=="1" (
  where gradle >nul 2>nul
  if %ERRORLEVEL%==0 (
    gradle %*
    exit /b %ERRORLEVEL%
  )
)
set BASE=%USERPROFILE%\.gradle\wrapper\dists\iconforge-gradle-%VERSION%
set GRADLE_HOME=%BASE%\gradle-%VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  set ARCHIVE=%BASE%\gradle-%VERSION%-bin.zip
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle-%VERSION%-bin.zip'; Expand-Archive -Force '%BASE%\gradle-%VERSION%-bin.zip' '%BASE%'"
  if errorlevel 1 exit /b 1
  if exist "%ARCHIVE%" del "%ARCHIVE%"
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%

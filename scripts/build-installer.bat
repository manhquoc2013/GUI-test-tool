@echo off
setlocal enabledelayedexpansion

set DIR=%~dp0..
cd /d "%DIR%"

set APP_NAME=TestStudio
set APP_DISPLAY_NAME=Test Studio
set VERSION=2.2.0
set JAR_NAME=playwright-automation-studio-%VERSION%.jar
set JPACKAGE_INPUT=target\jpackage-input
set JPACKAGE_OUT=target\jpackage-out
set DIST_DIR=dist

echo =================================================================
echo  Building %APP_DISPLAY_NAME% Full Offline Setup (.exe)
echo  Platform: Windows (x64) ^| Version: %VERSION%
echo =================================================================

if not defined JAVA_HOME (
  for /f "usebackq delims=" %%i in (`mvn -q help:evaluate -Dexpression=java.home -DforceStdout`) do set JAVA_HOME=%%i
)
if defined JAVA_HOME set PATH=%JAVA_HOME%\bin;%PATH%

where jpackage >nul 2>&1
if %ERRORLEVEL% neq 0 (
  echo ERROR: jpackage not found. Please install JDK 26.
  exit /b 1
)

echo [1/6] Compiling and packaging Java application...
call mvn -DskipTests clean package
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo [2/6] Staging jpackage input jars...
if exist %JPACKAGE_INPUT% rmdir /s /q %JPACKAGE_INPUT%
if exist %JPACKAGE_OUT% rmdir /s /q %JPACKAGE_OUT%
if not exist %DIST_DIR% mkdir %DIST_DIR%
mkdir %JPACKAGE_INPUT%\lib
copy target\%JAR_NAME% %JPACKAGE_INPUT%\ >nul
xcopy /e /y /q target\lib\* %JPACKAGE_INPUT%\lib\ >nul

echo [3/6] Creating self-contained application image via jpackage...
set ICON_ARG=
if exist assets\icon.ico (
  set ICON_ARG=--icon assets\icon.ico
)

jpackage --type app-image --name %APP_NAME% --input %JPACKAGE_INPUT% --main-jar %JAR_NAME% --main-class com.studio.App --dest %JPACKAGE_OUT% %ICON_ARG% --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.instrument,jdk.compiler,jdk.unsupported --java-options "-Dstudio.home=$ROOTDIR" --java-options "-Dfile.encoding=UTF-8" --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

set APP_IMAGE_DIR=%JPACKAGE_OUT%\%APP_NAME%
if not exist "%APP_IMAGE_DIR%" (
  echo ERROR: Expected app image directory not found at: %APP_IMAGE_DIR%
  exit /b 1
)

echo [4/6] Bundling Node.js LTS portable...
mkdir "%APP_IMAGE_DIR%\browsers"
mkdir "%APP_IMAGE_DIR%\tools\node"
mkdir "%APP_IMAGE_DIR%\tools\playwright-cli"
mkdir "%APP_IMAGE_DIR%\data\project"
mkdir "%APP_IMAGE_DIR%\data\reports"
mkdir "%APP_IMAGE_DIR%\data\config"
mkdir "%APP_IMAGE_DIR%\data\exports"
mkdir "%APP_IMAGE_DIR%\work"

if exist data\project\.gitkeep (
  copy /y data\project\.gitkeep "%APP_IMAGE_DIR%\data\project\.gitkeep" >nul
)

set NODE_VERSION=20.18.0
set NODE_URL=https://nodejs.org/dist/v%NODE_VERSION%/node-v%NODE_VERSION%-win-x64.zip
set NODE_ZIP=%TEMP%\node-v%NODE_VERSION%-win-x64.zip

if not exist "%NODE_ZIP%" (
  echo   Downloading Node.js %NODE_VERSION%...
  powershell -Command "Invoke-WebRequest -Uri '%NODE_URL%' -OutFile '%NODE_ZIP%'"
)

echo   Extracting Node.js into bundle...
powershell -Command "Expand-Archive -Path '%NODE_ZIP%' -DestinationPath '%TEMP%\node-extract' -Force"
xcopy /e /y /q "%TEMP%\node-extract\node-v%NODE_VERSION%-win-x64\*" "%APP_IMAGE_DIR%\tools\node\" >nul
mkdir "%APP_IMAGE_DIR%\tools\node\bin" 2>nul
if exist "%APP_IMAGE_DIR%\tools\node\node.exe" copy /y "%APP_IMAGE_DIR%\tools\node\node.exe" "%APP_IMAGE_DIR%\tools\node\bin\node.exe" >nul

echo [5/6] Bundling Playwright CLI and offline Chromium browser...
set PATH=%APP_IMAGE_DIR%\tools\node;%APP_IMAGE_DIR%\tools\node\bin;%PATH%
cd "%APP_IMAGE_DIR%\tools\playwright-cli"
call npm init -y > nul 2>&1
call npm install playwright@1.61.0 > nul 2>&1
cd /d "%DIR%"
set PLAYWRIGHT_BROWSERS_PATH=%APP_IMAGE_DIR%\browsers

if exist "%LOCALAPPDATA%\ms-playwright\chromium*" (
  echo   Pre-populating Chromium from local cache...
  xcopy /e /y /q "%LOCALAPPDATA%\ms-playwright\chromium*" "%APP_IMAGE_DIR%\browsers\" >nul 2>&1
  xcopy /e /y /q "%LOCALAPPDATA%\ms-playwright\ffmpeg*" "%APP_IMAGE_DIR%\browsers\" >nul 2>&1
)

if not exist "%APP_IMAGE_DIR%\browsers\chromium*" (
  echo   Downloading Chromium into bundled browsers directory...
  call npx --prefix "%APP_IMAGE_DIR%\tools\playwright-cli" playwright install chromium
)

echo   Verifying offline Chromium with Playwright Java...
call mvn -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium" >nul 2>&1

if not exist "%APP_IMAGE_DIR%\browsers\chromium*" (
  echo ERROR: Failed to bundle Chromium into %APP_IMAGE_DIR%\browsers! Setup would be incomplete.
  exit /b 1
)
echo   ^✓ Bundled Chromium verified in: %APP_IMAGE_DIR%\browsers

echo [6/6] Compiling Windows Setup Installer...
set "ISCC_EXE="
if exist "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" set "ISCC_EXE=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if exist "%ProgramFiles%\Inno Setup 6\ISCC.exe" set "ISCC_EXE=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if exist "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" set "ISCC_EXE=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
where iscc >nul 2>&1 && set "ISCC_EXE=iscc"
where ISCC.exe >nul 2>&1 && set "ISCC_EXE=ISCC.exe"

if not defined ISCC_EXE goto NO_INNO

echo   Using Inno Setup: "%ISCC_EXE%"
"%ISCC_EXE%" scripts\installer.iss
if %ERRORLEVEL% neq 0 (
  echo ERROR: Inno Setup compilation failed!
  exit /b %ERRORLEVEL%
)
echo =================================================================
echo  BUILD SUCCESSFUL!
echo  Setup Installer: %DIST_DIR%\TestStudio-%VERSION%-windows-x64-Setup.exe
echo =================================================================
goto BUILD_DONE

:NO_INNO
echo   Inno Setup not detected. Generating standard installer via jpackage...
jpackage --type exe --name %APP_NAME% --app-image %APP_IMAGE_DIR% --dest %DIST_DIR% --win-shortcut --win-menu --win-dir-chooser
if %ERRORLEVEL% neq 0 (
  echo   jpackage exe failed, trying msi...
  jpackage --type msi --name %APP_NAME% --app-image %APP_IMAGE_DIR% --dest %DIST_DIR% --win-shortcut --win-menu --win-dir-chooser
)
echo =================================================================
echo  BUILD SUCCESSFUL!
echo  Check output in: %DIST_DIR%\
echo =================================================================

:BUILD_DONE


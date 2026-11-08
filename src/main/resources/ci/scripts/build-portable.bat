@echo off
setlocal enabledelayedexpansion

set DIR=%~dp0..
cd /d "%DIR%"

set APP_NAME=TestStudio
set VERSION=
for /f "usebackq delims=" %%v in (`mvn -q help:evaluate -Dexpression=project.version -DforceStdout 2^>nul`) do set VERSION=%%v
if not defined VERSION set VERSION=2.3.1
set JAR_NAME=playwright-automation-studio-%VERSION%.jar
set DIST_ROOT=dist\%APP_NAME%
set JPACKAGE_INPUT=target\jpackage-input
set JPACKAGE_OUT=target\jpackage-out

echo === Building %APP_NAME% Portable (jpackage app-image) ===

if not defined JAVA_HOME (
  for /f "usebackq delims=" %%i in (`mvn -q help:evaluate -Dexpression=java.home -DforceStdout`) do set JAVA_HOME=%%i
)
if defined JAVA_HOME set PATH=%JAVA_HOME%\bin;%PATH%

where jpackage >nul 2>&1
if %ERRORLEVEL% neq 0 (
  echo ERROR: jpackage not found. Install a full JDK 26.
  exit /b 1
)
jpackage --version

echo Step 1: Building application...
call mvn -DskipTests clean package
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo Step 2: Staging jpackage input...
if exist dist rmdir /s /q dist
if exist %JPACKAGE_INPUT% rmdir /s /q %JPACKAGE_INPUT%
if exist %JPACKAGE_OUT% rmdir /s /q %JPACKAGE_OUT%
mkdir %JPACKAGE_INPUT%\lib
copy target\%JAR_NAME% %JPACKAGE_INPUT%\ >nul
xcopy /e /y /q target\lib\* %JPACKAGE_INPUT%\lib\ >nul

echo Step 3: Creating app-image with embedded JRE (including javac compiler)...
jpackage --type app-image --name %APP_NAME% --input %JPACKAGE_INPUT% --main-jar %JAR_NAME% --main-class com.studio.Launcher --dest %JPACKAGE_OUT% --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.instrument,java.scripting,java.xml,java.net.http,jdk.compiler,jdk.unsupported --java-options "-Dstudio.home=$ROOTDIR" --java-options "-Dfile.encoding=UTF-8" --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo Step 4: Assembling portable layout...
mkdir %DIST_ROOT%
xcopy /e /y /q %JPACKAGE_OUT%\%APP_NAME%\* %DIST_ROOT%\ >nul
set BUNDLE_ROOT=%DIST_ROOT%

mkdir "%BUNDLE_ROOT%\data\project"
mkdir "%BUNDLE_ROOT%\data\reports"
mkdir "%BUNDLE_ROOT%\data\config"
mkdir "%BUNDLE_ROOT%\data\exports"
mkdir "%BUNDLE_ROOT%\work"
mkdir "%BUNDLE_ROOT%\browsers"
mkdir "%BUNDLE_ROOT%\tools\node"
mkdir "%BUNDLE_ROOT%\tools\playwright-cli"

if exist data\project\.gitkeep (
  copy /y data\project\.gitkeep "%BUNDLE_ROOT%\data\project\.gitkeep" >nul
)

echo Step 5: Setting up Node.js portable...
set NODE_VERSION=20.18.0
set NODE_URL=https://nodejs.org/dist/v%NODE_VERSION%/node-v%NODE_VERSION%-win-x64.zip
set NODE_ZIP=%TEMP%\node-v%NODE_VERSION%-win-x64.zip

if not exist "%NODE_ZIP%" (
  echo   Downloading Node.js %NODE_VERSION%...
  powershell -Command "Invoke-WebRequest -Uri '%NODE_URL%' -OutFile '%NODE_ZIP%'"
)

echo   Extracting Node.js...
powershell -Command "Expand-Archive -Path '%NODE_ZIP%' -DestinationPath '%TEMP%\node-extract' -Force"
xcopy /e /y /q "%TEMP%\node-extract\node-v%NODE_VERSION%-win-x64\*" "%BUNDLE_ROOT%\tools\node\" >nul
mkdir "%BUNDLE_ROOT%\tools\node\bin" 2>nul
if exist "%BUNDLE_ROOT%\tools\node\node.exe" copy /y "%BUNDLE_ROOT%\tools\node\node.exe" "%BUNDLE_ROOT%\tools\node\bin\node.exe" >nul

echo Step 6: Installing Playwright CLI + Chromium (Offline bundle)...
set PATH=%BUNDLE_ROOT%\tools\node;%BUNDLE_ROOT%\tools\node\bin;%PATH%
cd "%BUNDLE_ROOT%\tools\playwright-cli"
call npm init -y > nul 2>&1
call npm install playwright@1.61.0 > nul 2>&1
cd /d "%DIR%"
set PLAYWRIGHT_BROWSERS_PATH=%BUNDLE_ROOT%\browsers

if exist "%LOCALAPPDATA%\ms-playwright\chromium*" (
  echo   Pre-populating Chromium from local cache...
  xcopy /e /y /q "%LOCALAPPDATA%\ms-playwright\chromium*" "%BUNDLE_ROOT%\browsers\" >nul 2>&1
  xcopy /e /y /q "%LOCALAPPDATA%\ms-playwright\ffmpeg*" "%BUNDLE_ROOT%\browsers\" >nul 2>&1
)

if not exist "%BUNDLE_ROOT%\browsers\chromium*" (
  echo   Downloading Chromium into bundled browsers directory...
  call npx --prefix "%BUNDLE_ROOT%\tools\playwright-cli" playwright install chromium
)

echo   Verifying Chromium with Playwright Java CLI...
call mvn -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium" >nul 2>&1

if not exist "%BUNDLE_ROOT%\browsers\chromium*" (
  echo ERROR: Failed to bundle Chromium into %BUNDLE_ROOT%\browsers! Portable distribution incomplete.
  exit /b 1
)
echo   Bundled Chromium verified in: %BUNDLE_ROOT%\browsers

echo Step 7: Creating launcher...
(
echo @echo off
echo setlocal
echo set APP_HOME=%%~dp0
echo set PLAYWRIGHT_BROWSERS_PATH=%%APP_HOME%%browsers
echo set PATH=%%APP_HOME%%tools\node;%%PATH%%
echo "%%APP_HOME%%%APP_NAME%.exe" %%*
echo if %%ERRORLEVEL%% neq 0 (
echo   echo.
echo   echo [ERROR] %APP_NAME% exited with code %%ERRORLEVEL%%.
echo   echo Check data\logs\startup-error.log for diagnostic details.
echo   pause
echo )
) > "%DIST_ROOT%\run.bat"

(
echo Test Studio - Portable Edition
echo ================================================
echo.
echo To run: double-click run.bat or %APP_NAME%.exe
echo.
echo This build embeds a full Java runtime via jpackage — no host JDK required.
echo Node.js and Playwright Chromium are also bundled.
) > "%DIST_ROOT%\README.txt"

echo Step 8: Creating zip archive...
cd dist
powershell -Command "Compress-Archive -Path '%APP_NAME%' -DestinationPath '%APP_NAME%-win-x64.zip' -Force"
echo Portable zip created: dist\%APP_NAME%-win-x64.zip
cd ..

echo === Build Complete ===
echo Portable app: dist\%APP_NAME%\
echo Zip archive:  dist\%APP_NAME%-win-x64.zip

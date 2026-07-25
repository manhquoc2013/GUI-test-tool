@echo off
set DIR=%~dp0..
set DIST=%DIR%\dist\PlaywrightAutomationStudio
rmdir /s /q "%DIST%" 2>nul
mkdir "%DIST%\data\config" 2>nul & mkdir "%DIST%\data\project" 2>nul
mkdir "%DIST%\data\reports" 2>nul & mkdir "%DIST%\data\exports" 2>nul
mkdir "%DIST%\work" 2>nul
cd /d "%DIR%"
mvn -B -DskipTests package
copy target\playwright-automation-studio-1.0.0.jar "%DIST%\PlaywrightAutomationStudio.jar"
(echo @echo off & echo start javaw -jar "%%~dp0PlaywrightAutomationStudio.jar") > "%DIST%\run.bat"
echo Portable: %DIST%

@echo off
REM Baut Nivellement.jar (Java 17+)
if exist build rmdir /s /q build
mkdir build
if not exist dist mkdir dist
javac --release 17 -encoding UTF-8 -d build src\nivellement\*.java || goto fehler
xcopy /q /i src\nivellement\bilder build\nivellement\bilder >nul
xcopy /q /i src\nivellement\hilfe build\nivellement\hilfe >nul
jar cfm dist\Nivellement.jar MANIFEST.MF -C build . || goto fehler
echo Fertig: dist\Nivellement.jar
goto ende
:fehler
echo Fehler beim Bauen.
:ende
pause

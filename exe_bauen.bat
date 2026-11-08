@echo off
REM Erstellt ein Windows-Programm mit eingebetteter Java-Laufzeit (benoetigt JDK 17+ mit jpackage)
call bauen.bat
jpackage --type app-image --name Nivellement --input dist --main-jar Nivellement.jar --main-class nivellement.Main --app-version 1.00 --vendor "Dipl.-Ing. (FH) Stefan Klein" --copyright "Dipl.-Ing. (FH) Stefan Klein" --dest dist\exe
echo Fertig: dist\exe\Nivellement\Nivellement.exe
pause

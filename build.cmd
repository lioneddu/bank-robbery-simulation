@echo off
rem Compila il progetto e crea dist\bank-robbery.jar (richiede un JDK 11 o superiore).
setlocal
cd /d "%~dp0"
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes dist
dir /s /b src\*.java > build\sources.txt
javac --release 11 -encoding UTF-8 -nowarn -d build\classes @build\sources.txt || exit /b 1
copy /y src\simulation.properties build\classes\ > nul
jar --create --file dist\bank-robbery.jar --main-class alloverdev.Main -C build\classes . || exit /b 1
echo Creato dist\bank-robbery.jar - avvialo con: java -jar dist\bank-robbery.jar

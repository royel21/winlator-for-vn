@echo off
setlocal enabledelayedexpansion

rem Get the current folder path
set "folder=%~dp0"

set "INTEXTFILE=%folder%temp.ini"
set "OUTTEXTFILE=%folder%jyuso_inst.ini"
set "SEARCHTEXT=folder"

if "%folder:~-1%"=="\" set "folder=%folder:~0,-1%"
echo Input file: %INTEXTFILE%
echo Output file: %OUTTEXTFILE%

if exist "%OUTTEXTFILE%" del "%OUTTEXTFILE%"

for /f "usebackq tokens=* delims=" %%A in ("%INTEXTFILE%") do (
    set "string=%%A"
    set "modified=!string:%SEARCHTEXT%=%folder%!"
    echo !modified!>>"%OUTTEXTFILE%"
)
copy "%OUTTEXTFILE%" C:\Windows\
endlocal

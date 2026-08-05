
setlocal

rem Get the current folder path
set "folder=%~dp0"

set "INTEXTFILE=%folder%temp.ini"
set "OUTTEXTFILE=%folder%jyuso_inst.ini"
set SEARCHTEXT=folder

if "%folder:~-1%"=="\" set "folder=%folder:~0,-1%"
echo %INTEXTFILE%
DEL "%OUTTEXTFILE%"

for /f "tokens=1,* delims=¶" %%A in ('type "%INTEXTFILE%"') do (
    set string=%%A
    set modified=!string:%SEARCHTEXT%=%folder%!
    echo %modified%
    echo !modified! >> "%OUTTEXTFILE%"
)
pause
endlocal
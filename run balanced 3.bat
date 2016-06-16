::@echo off 
del /Q output\solutions.csv
set MYPROG=java -jar solverVRP.jar

for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1 b 3
)
for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1.1 b 3
)
for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1.2 b 3
)
for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1.3 b 3
)

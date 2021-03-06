::Usage:
::vrp.jar -i rc101.txt 1.2		unbalanced
::vrp.jar -i rc101.txt 1.2 b		balanced with default rate max/min = 2
::vrp.jar -i rc101.txt 1.2 b 3.2	balanced with custom rate max/min
::@echo off 
del /Q output\solutions.csv
set MYPROG=java -jar solverVRP.jar

for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1 b
)
for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1.1 b
)
for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1.2 b
)
for /F %%i in (files.txt) do (
	echo %MYPROG% -i %%i
	%MYPROG% -i %%i 1.3 b
)

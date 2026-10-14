@echo off
rem Fill the running demo server with sample school data (run once after start-demo.bat).
cd /d "%~dp0..\.."
set PYTHONIOENCODING=utf-8
python scripts\demo\seed_demo.py %*
pause

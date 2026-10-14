@echo off
rem Wipe the demo database and demo files. Stop start-demo.bat first.
setlocal
cd /d "%~dp0..\.."
choice /m "Delete ALL demo data (database ittools_demo and data\demo-storage)"
if errorlevel 2 goto :eof
docker compose exec -T db psql -U ittools -d postgres -c "DROP DATABASE IF EXISTS ittools_demo WITH (FORCE)"
docker compose exec -T db psql -U ittools -d postgres -c "CREATE DATABASE ittools_demo OWNER ittools"
if exist data\demo-storage rmdir /s /q data\demo-storage
echo Done. Run start-demo.bat, then: python scripts\demo\seed_demo.py
pause

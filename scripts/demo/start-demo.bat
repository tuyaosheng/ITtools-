@echo off
rem ITtools demo server: separate database (ittools_demo) and storage (data\demo-storage),
rem so testing never touches development data. Open http://localhost:8300 after startup.
setlocal
cd /d "%~dp0..\.."

echo [1/3] Starting PostgreSQL container ...
docker compose up -d db || goto :error

echo [2/3] Preparing demo database ittools_demo ...
:waitdb
docker compose exec -T db pg_isready -U ittools >nul 2>&1 || (timeout /t 2 >nul & goto :waitdb)
docker compose exec -T db psql -U ittools -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='ittools_demo'" | findstr 1 >nul
if errorlevel 1 docker compose exec -T db psql -U ittools -d postgres -c "CREATE DATABASE ittools_demo OWNER ittools" || goto :error

echo [3/3] Starting ITtools on http://localhost:8300  (press Ctrl+C to stop)
set DB_URL=jdbc:postgresql://localhost:5432/ittools_demo
set STORAGE_ROOT=data\demo-storage
set PYTHON_RUNNER=docker
java -jar ittools-app\target\ittools-app-0.1.0-SNAPSHOT.jar
goto :eof

:error
echo.
echo Failed. Make sure Docker Desktop is running and the jar has been built.
pause

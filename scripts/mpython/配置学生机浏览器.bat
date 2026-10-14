@echo off
rem Configure Edge/Chrome on a student PC so the mPython board page can use WebSerial.
rem Usage: double-click, or pass the teacher address, e.g. 192.168.1.10:8300
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0configure-browser.ps1" %*

@echo off
rem Start metric-service HTTP API on port 8090
cd /d E:\python\project\metric-service
.venv\Scripts\activate
python api_server.py

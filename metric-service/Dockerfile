# metric-service（Python）：RAGAS 评测服务
FROM python:3.12-slim
WORKDIR /app
COPY metric-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY metric-service/ .
EXPOSE 8090
CMD ["python", "api_server.py"]

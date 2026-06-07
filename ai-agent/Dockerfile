FROM python:3.11-slim
WORKDIR /app

# Azure Speech SDK 运行时依赖
RUN apt-get update \
    && apt-get install -y --no-install-recommends libasound2 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 50051 8089
CMD ["python", "main.py"]

COPY . .
EXPOSE 50051 8089
CMD ["python", "main.py"]

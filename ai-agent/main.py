import asyncio
import os
import grpc
from fastapi import FastAPI
from prometheus_client import make_asgi_app
import uvicorn
from loguru import logger
import redis.asyncio as redis
from grpc_health.v1 import health_pb2, health_pb2_grpc

app = FastAPI(title="SilverTongue AI Agent")

# ==========================================
# 1. Prometheus Metrics Configuration
# ==========================================
metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)

# ==========================================
# 2. Redis Configuration (DB 3)
# ==========================================
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/3")
redis_client = None

@app.on_event("startup")
async def startup_event():
    global redis_client
    redis_client = redis.from_url(REDIS_URL)
    logger.info(f"Connected to Redis at {REDIS_URL}")

@app.on_event("shutdown")
async def shutdown_event():
    if redis_client:
        await redis_client.close()
        logger.info("Redis connection closed.")

# ==========================================
# 3. FastAPI Health Endpoint
# ==========================================
@app.get("/health")
async def health_check():
    redis_status = "ok" if redis_client else "disconnected"
    return {"status": "ok", "redis": redis_status}

# ==========================================
# 4. gRPC Health Check Servicer
# ==========================================
class HealthServicer(health_pb2_grpc.HealthServicer):
    def Check(self, request, context):
        return health_pb2.HealthCheckResponse(status=health_pb2.HealthCheckResponse.SERVING)
        
    def Watch(self, request, context):
        context.abort(grpc.StatusCode.UNIMPLEMENTED, "Watch is not implemented")

# Import our custom servicers
from services.agent_service import AgentServiceServicer
from services.assessment_service import AssessmentServiceServicer
import agent_pb2_grpc
import assessment_pb2_grpc

# ==========================================
# 5. Service Startup
# ==========================================
async def serve_grpc():
    server = grpc.aio.server()
    
    # Register Health Check
    health_pb2_grpc.add_HealthServicer_to_server(HealthServicer(), server)
    
    # Register AgentService and AssessmentService
    agent_pb2_grpc.add_AgentServiceServicer_to_server(AgentServiceServicer(), server)
    assessment_pb2_grpc.add_AssessmentServiceServicer_to_server(AssessmentServiceServicer(), server)
    
    listen_addr = '[::]:50051'
    server.add_insecure_port(listen_addr)
    logger.info(f"Starting gRPC server on {listen_addr}")
    await server.start()
    await server.wait_for_termination()

async def serve_fastapi():
    config = uvicorn.Config(app, host="0.0.0.0", port=8089, log_level="info")
    server = uvicorn.Server(config)
    logger.info("Starting FastAPI server on port 8089")
    await server.serve()

async def main():
    logger.info("Starting SilverTongue AI Agent Services...")
    await asyncio.gather(
        serve_grpc(),
        serve_fastapi()
    )

if __name__ == "__main__":
    asyncio.run(main())

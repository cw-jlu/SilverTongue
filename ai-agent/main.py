"""
SilverTongue AI Agent — Entrypoint
====================================
Starts gRPC + FastAPI servers, initialises the model router,
STT/TTS services, Kafka audio pipeline, and ES store.
"""

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
# 3. FastAPI Health & Info Endpoints
# ==========================================
@app.get("/health")
async def health_check():
    redis_status = "ok" if redis_client else "disconnected"
    return {"status": "ok", "redis": redis_status}

@app.get("/providers")
async def list_providers():
    """Admin endpoint: list all registered model providers and their status."""
    return {"providers": _model_router.list_providers() if _model_router else []}

@app.post("/providers/refresh")
async def refresh_providers():
    """Admin endpoint: re-run health checks on all providers."""
    if _model_router:
        _model_router.refresh()
    return {"status": "refreshed"}

# ==========================================
# 4. gRPC Health Check Servicer
# ==========================================
class HealthServicer(health_pb2_grpc.HealthServicer):
    def Check(self, request, context):
        return health_pb2.HealthCheckResponse(status=health_pb2.HealthCheckResponse.SERVING)

    def Watch(self, request, context):
        context.abort(grpc.StatusCode.UNIMPLEMENTED, "Watch is not implemented")

# ==========================================
# 5. Service Initialisation
# ==========================================

# Global singletons (initialised in _init_services)
_model_router = None
_stt_service = None
_tts_service = None
_conversation_store = None
_audio_pipeline = None

def _init_services():
    """
    Initialise all core services:
        1. ModelRouter     — multi-model provider registry
        2. STTService      — speech-to-text (local/API)
        3. TTSService      — text-to-speech (edge-tts/API)
        4. ConversationStore — ES persistence
        5. AudioPipeline   — Kafka produce/consume + STT orchestration
        6. Inject deps into LangGraph nodes and agent_service
    """
    global _model_router, _stt_service, _tts_service, _conversation_store, _audio_pipeline

    logger.info("=" * 60)
    logger.info("Initialising SilverTongue AI Agent Services")
    logger.info("=" * 60)

    # 1. Model Router
    from services.model_router import ModelRouter
    _model_router = ModelRouter()

    # 2. STT
    from services.stt_service import STTService
    _stt_service = STTService()

    # 3. TTS
    from services.tts_service import TTSService
    _tts_service = TTSService()

    # 4. ES Store
    from services.conversation_store import ConversationStore
    _conversation_store = ConversationStore()

    # 5. Audio Pipeline
    from services.audio_pipeline import AudioPipeline
    _audio_pipeline = AudioPipeline(
        stt=_stt_service,
        store=_conversation_store,
    )
    _audio_pipeline.start_consumer()

    # 6. Inject into LangGraph nodes
    from agent.nodes import configure_nodes
    configure_nodes(_model_router, _stt_service, _tts_service, _conversation_store)

    # 7. Inject into agent_service
    from services.agent_service import configure_agent_service
    configure_agent_service(_audio_pipeline, _conversation_store)

    logger.info("=" * 60)
    logger.info("All services initialised successfully")
    logger.info("=" * 60)


# Import gRPC servicers (after defining health)
from services.agent_service import AgentServiceServicer
from services.assessment_service import AssessmentServiceServicer
import agent_pb2_grpc
import assessment_pb2_grpc

# ==========================================
# 6. Server Startup
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
    logger.info("Starting SilverTongue AI Agent...")

    # Init all services before starting servers
    _init_services()

    await asyncio.gather(
        serve_grpc(),
        serve_fastapi()
    )

if __name__ == "__main__":
    asyncio.run(main())

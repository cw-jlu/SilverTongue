"""
SilverTongue AI Agent — Entrypoint
====================================
Starts gRPC + FastAPI servers, initialises the model router,
STT/TTS services, Kafka audio pipeline, and ES store.
"""

import asyncio
import os
import grpc
from fastapi import FastAPI, Body, Response, status
from prometheus_client import make_asgi_app
import uvicorn
from loguru import logger
import redis.asyncio as redis
from grpc_health.v1 import health_pb2, health_pb2_grpc
import base64
from pydantic import BaseModel, ConfigDict, Field

app = FastAPI(title="SilverTongue AI Agent")
_harvester_tasks = set()


class HarvesterJobRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    clip_id: int = Field(alias="clipId")
    url: str
    start_time: float = Field(alias="startTime")
    end_time: float = Field(alias="endTime")

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

@app.get("/api/ai/roles")
async def get_preset_roles():
    from agent.nodes import PRESET_ROLES
    roles_desc = {
        "日常闲聊": "友善、轻松的聊天伴侣，适合练习日常英语对话",
        "雅思考官": "模拟雅思口语考试，包含Part 1, 2, 3的专业测评",
        "外企 HR 面试": "模拟外企求职面试，提供专业的行为与技术问题提问",
        "商务会议": "模拟商务会议场景，讨论项目进度、战略规划等",
        "旅游向导": "热情好客的当地导游，带你领略名胜古迹并推荐地道美食",
        "餐厅点餐": "高档餐厅的服务员，为你提供点餐、配酒等沉浸式体验"
    }
    return [
        {
            "id": idx + 1,
            "name": k,
            "description": roles_desc.get(k, "自定义AI角色"),
            "setting": v,
            "type": "preset"
        }
        for idx, (k, v) in enumerate(PRESET_ROLES.items())
    ]

@app.post("/api/ai/tts/speak")
async def tts_speak(payload: dict = Body(...)):
    text = payload.get("text", "")
    voice = payload.get("voice", "")
    voice_map = {
        "female_us": "en-US-AriaNeural",
        "male_us": "en-US-GuyNeural",
        "female_uk": "en-GB-SoniaNeural",
        "male_uk": "en-GB-RyanNeural"
    }
    actual_voice = voice_map.get(voice, voice)
    audio_bytes = b""
    if _tts_service:
        audio_bytes = _tts_service.synthesize(text, voice=actual_voice)
    return Response(content=audio_bytes, media_type="audio/mpeg")

@app.post("/api/ai/stt/transcribe")
async def stt_transcribe(payload: dict = Body(...)):
    audio_base64 = payload.get("audioBase64", "")
    try:
        audio_bytes = base64.b64decode(audio_base64)
    except Exception as e:
        logger.error(f"Failed to decode base64: {e}")
        return {"text": ""}
    text = ""
    if _stt_service:
        text = _stt_service.transcribe(audio_bytes)
    return {"text": text}


async def _run_harvester_job(job: HarvesterJobRequest):
    from services.harvester import notify_backend, process_clip

    logger.info(
        "Running harvester job clip_id={}, url={}, start_time={}, end_time={}",
        job.clip_id,
        job.url,
        job.start_time,
        job.end_time,
    )
    await asyncio.to_thread(
        process_clip,
        job.clip_id,
        job.url,
        job.start_time,
        job.end_time,
    )
    return job.clip_id


def _on_harvester_task_done(task: asyncio.Task, clip_id: int | None = None):
    _harvester_tasks.discard(task)
    try:
        task.result()
    except Exception as exc:
        if clip_id is not None:
            try:
                from services.harvester import notify_backend

                notify_backend(clip_id, 4, "")
            except Exception as notify_exc:
                logger.exception("Failed to notify backend for failed harvester job {}: {}", clip_id, notify_exc)
        logger.exception("Harvester job failed: {}", exc)


@app.post("/api/harvester/jobs", status_code=status.HTTP_202_ACCEPTED)
async def create_harvester_job(payload: HarvesterJobRequest):
    task = asyncio.create_task(_run_harvester_job(payload))
    _harvester_tasks.add(task)
    task.add_done_callback(lambda done_task: _on_harvester_task_done(done_task, payload.clip_id))
    return {
        "status": "accepted",
        "clipId": payload.clip_id,
    }


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
from services.dictionary_service import DictionaryServiceServicer
import agent_pb2_grpc
import assessment_pb2_grpc
import dictionary_pb2_grpc

# ==========================================
# 6. Server Startup
# ==========================================
async def serve_grpc():
    server = grpc.aio.server()

    # Register Health Check
    health_pb2_grpc.add_HealthServicer_to_server(HealthServicer(), server)

    # Register AgentService, AssessmentService, and DictionaryService
    agent_pb2_grpc.add_AgentServiceServicer_to_server(AgentServiceServicer(), server)
    assessment_pb2_grpc.add_AssessmentServiceServicer_to_server(AssessmentServiceServicer(), server)
    dictionary_pb2_grpc.add_DictionaryServiceServicer_to_server(DictionaryServiceServicer(), server)

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

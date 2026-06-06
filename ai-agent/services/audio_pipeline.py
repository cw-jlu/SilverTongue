"""
Kafka-based Audio Pipeline
=============================
Ensures that **all** user audio is transcribed and persisted,
regardless of whether the selected model supports native voice.

Flow:
    produce(audio, session_id)
        → Kafka "st-audio-raw"
            → consume thread → STT → text
                → persist to ES (st_conversation_index)
                → callback with transcribed text

In single-node / dev mode (no Kafka), falls back to a direct
in-process STT call so the system remains functional.
"""

import os
import json
import threading
import uuid
from typing import Callable, Optional
from loguru import logger

from services.stt_service import STTService
from services.conversation_store import ConversationStore


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "")
TOPIC_AUDIO_RAW = "st-audio-raw"


class AudioPipeline:
    """
    Publish user audio for async STT processing and ES persistence.

    Args:
        stt: An initialised STTService instance.
        store: An initialised ConversationStore instance.
        on_transcribed: Optional callback ``(session_id, user_id, text) -> None``
                        invoked after each successful transcription.
    """

    def __init__(
        self,
        stt: STTService,
        store: ConversationStore,
        on_transcribed: Optional[Callable] = None,
    ):
        self.stt = stt
        self.store = store
        self.on_transcribed = on_transcribed
        self._producer = None
        self._consumer_thread: Optional[threading.Thread] = None
        self._running = False

        if KAFKA_BOOTSTRAP:
            self._init_kafka()
        else:
            logger.info("AudioPipeline: Kafka not configured — using in-process STT")

    # ---- Kafka init --------------------------------------------------------

    def _init_kafka(self):
        try:
            from kafka import KafkaProducer
            self._producer = KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP,
                value_serializer=lambda v: json.dumps(v).encode("utf-8") if isinstance(v, dict) else v,
                key_serializer=lambda k: k.encode("utf-8") if isinstance(k, str) else k,
            )
            logger.info(f"AudioPipeline: Kafka producer connected to {KAFKA_BOOTSTRAP}")
        except Exception as e:
            logger.error(f"AudioPipeline: failed to create Kafka producer: {e}")
            self._producer = None

    def start_consumer(self):
        """Start the background Kafka consumer thread."""
        if not KAFKA_BOOTSTRAP or self._consumer_thread is not None:
            return
        self._running = True
        self._consumer_thread = threading.Thread(
            target=self._consume_loop, daemon=True, name="audio-stt-consumer",
        )
        self._consumer_thread.start()
        logger.info("AudioPipeline: Kafka consumer thread started")

    def stop_consumer(self):
        self._running = False
        if self._consumer_thread:
            self._consumer_thread.join(timeout=5)
            self._consumer_thread = None

    def _consume_loop(self):
        """Background loop: consume audio from Kafka → STT → persist."""
        try:
            from kafka import KafkaConsumer
            consumer = KafkaConsumer(
                TOPIC_AUDIO_RAW,
                bootstrap_servers=KAFKA_BOOTSTRAP,
                group_id="st-stt-group",
                auto_offset_reset="latest",
                value_deserializer=lambda v: json.loads(v.decode("utf-8")),
                consumer_timeout_ms=1000,
            )
        except Exception as e:
            logger.error(f"AudioPipeline consumer init failed: {e}")
            return

        logger.info(f"AudioPipeline consumer subscribed to {TOPIC_AUDIO_RAW}")
        while self._running:
            try:
                records = consumer.poll(timeout_ms=500)
                for tp, messages in records.items():
                    for msg in messages:
                        self._handle_audio_message(msg.value)
            except Exception as e:
                logger.error(f"AudioPipeline consumer error: {e}")

        consumer.close()
        logger.info("AudioPipeline consumer stopped")

    def _handle_audio_message(self, payload: dict):
        """Process a single audio message from Kafka."""
        import base64
        session_id = payload.get("session_id", "")
        user_id = payload.get("user_id", "")
        audio_b64 = payload.get("audio_b64", "")

        if not audio_b64:
            return

        audio_bytes = base64.b64decode(audio_b64)
        text = self.stt.transcribe(audio_bytes)

        if text:
            # Persist user message to ES
            self.store.persist_message(
                msg_id=str(uuid.uuid4()),
                session_id=session_id,
                user_id=user_id,
                role="user",
                content=text,
            )
            # Invoke callback (e.g. push to frontend)
            if self.on_transcribed:
                try:
                    self.on_transcribed(session_id, user_id, text)
                except Exception as e:
                    logger.error(f"on_transcribed callback error: {e}")

            logger.info(f"AudioPipeline: transcribed session={session_id}: {text[:80]}...")

    # ---- Public API --------------------------------------------------------

    def produce(self, audio_data: bytes, session_id: str, user_id: str):
        """
        Submit audio for async STT processing.
        If Kafka is not available, processes in-line synchronously.
        """
        import base64
        audio_b64 = base64.b64encode(audio_data).decode("utf-8")
        payload = {
            "session_id": session_id,
            "user_id": user_id,
            "audio_b64": audio_b64,
        }

        if self._producer:
            try:
                self._producer.send(TOPIC_AUDIO_RAW, key=session_id, value=payload)
                self._producer.flush()
                return
            except Exception as e:
                logger.error(f"Kafka produce failed, falling back to in-process: {e}")

        # Fallback: process in-line
        self._handle_audio_message(payload)

    def transcribe_sync(self, audio_data: bytes) -> str:
        """
        Direct synchronous transcription (skips Kafka).
        Used when the caller needs the text immediately.
        """
        return self.stt.transcribe(audio_data)

"""
Conversation Store (Elasticsearch)
====================================
Persists all dialogue messages (user + AI) to the
``st_conversation_index`` for full-text search and history replay.

Index mapping follows Database_Design.md §6.3.
"""

import os
import uuid
from datetime import datetime, timezone
from typing import Optional
from loguru import logger


class ConversationStore:
    """
    Write conversation messages to Elasticsearch.

    Initialises the ES client lazily and creates the index
    with proper mappings if it does not yet exist.
    """

    INDEX_NAME = "st_conversation_index"

    def __init__(self):
        self._es = None
        self._init_es()

    def _init_es(self):
        es_url = os.getenv("ES_URIS", "http://localhost:9200")
        try:
            from elasticsearch import Elasticsearch
            self._es = Elasticsearch(es_url)
            if self._es.ping():
                logger.info(f"ConversationStore: connected to ES at {es_url}")
                self._ensure_index()
            else:
                logger.warning(f"ConversationStore: ES ping failed at {es_url}")
                self._es = None
        except ImportError:
            logger.warning("ConversationStore: elasticsearch package not installed")
        except Exception as e:
            logger.error(f"ConversationStore: ES init failed: {e}")
            self._es = None

    def _ensure_index(self):
        """Create the index with IK analyzer mappings if it doesn't exist."""
        if self._es.indices.exists(index=self.INDEX_NAME):
            return
        mappings = {
            "mappings": {
                "properties": {
                    "msg_id":           {"type": "keyword"},
                    "session_id":       {"type": "keyword"},
                    "user_id":          {"type": "keyword"},
                    "role":             {"type": "keyword"},
                    "content":          {"type": "text", "analyzer": "standard"},
                    "refined_content":  {"type": "text", "analyzer": "standard"},
                    "create_time":      {"type": "date"},
                }
            }
        }
        try:
            self._es.indices.create(index=self.INDEX_NAME, body=mappings)
            logger.info(f"ConversationStore: created index {self.INDEX_NAME}")
        except Exception as e:
            logger.error(f"ConversationStore: failed to create index: {e}")

    # ---- public API --------------------------------------------------------

    def persist_message(
        self,
        msg_id: Optional[str] = None,
        session_id: str = "",
        user_id: str = "",
        role: str = "user",
        content: str = "",
        refined_content: str = "",
    ):
        """
        Write a single message document to the conversation index.

        Args:
            msg_id: Unique message ID (auto-generated if omitted).
            session_id: The session this message belongs to.
            user_id: The user who owns the session.
            role: ``user`` or ``assistant``.
            content: Raw text content.
            refined_content: AI-refined native rewrite (optional).
        """
        if not self._es:
            logger.debug("ConversationStore: ES not available, skipping persist")
            return

        doc = {
            "msg_id": msg_id or str(uuid.uuid4()),
            "session_id": session_id,
            "user_id": user_id,
            "role": role,
            "content": content,
            "refined_content": refined_content,
            "create_time": datetime.now(timezone.utc).isoformat(),
        }

        try:
            self._es.index(index=self.INDEX_NAME, body=doc)
            logger.debug(f"ConversationStore: persisted {role} msg for session={session_id}")
        except Exception as e:
            logger.error(f"ConversationStore: persist failed: {e}")

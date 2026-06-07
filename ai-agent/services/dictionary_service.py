"""
DictionaryService gRPC Servicer
================================
Cambridge Dictionary SQLite lookup — mirrors Enjoy App's camdict.ts.

Reads from ``cam_dict.refined.sqlite``, which has a ``camdict`` table
with columns: id, oid, word, pos_items (JSON).

Configure ``DICT_SQLITE_PATH`` env var to point to the SQLite file.
Fallback: returns a mock entry for integration testing.
"""

import os
import sqlite3
import json
import sys
import grpc
from loguru import logger

# Import generated stubs
sys.path.append(os.path.dirname(__file__))
import dictionary_pb2
import dictionary_pb2_grpc


# ---------------------------------------------------------------------------
# SQLite lookup
# ---------------------------------------------------------------------------

class CambridgeDict:
    """Thin wrapper around cam_dict.refined.sqlite."""

    def __init__(self):
        db_path = os.getenv(
            "DICT_SQLITE_PATH",
            os.path.join(os.path.dirname(__file__), "..", "cam_dict.refined.sqlite"),
        )
        self.db_path = db_path
        self._conn = None

    @property
    def conn(self) -> sqlite3.Connection | None:
        if self._conn is None:
            if not os.path.exists(self.db_path):
                logger.warning(f"Dictionary SQLite not found at {self.db_path}")
                return None
            self._conn = sqlite3.connect(self.db_path)
            self._conn.row_factory = sqlite3.Row
        return self._conn

    def lookup(self, word: str) -> dict | None:
        """Look up a word in the SQLite database. Returns row dict or None."""
        if self.conn is None:
            return None
        try:
            row = self.conn.execute(
                "SELECT * FROM camdict WHERE LOWER(word) = LOWER(?) LIMIT 1",
                (word.strip(),),
            ).fetchone()
            return dict(row) if row else None
        except sqlite3.OperationalError as e:
            logger.error(f"SQLite lookup error for '{word}': {e}")
            return None


# Singleton
_cam_dict = CambridgeDict()


# ---------------------------------------------------------------------------
# gRPC Servicer
# ---------------------------------------------------------------------------

class DictionaryServiceServicer(dictionary_pb2_grpc.DictionaryServiceServicer):
    """Implements LookupWord RPC."""

    def LookupWord(self, request, context):
        word = request.word.strip()
        if not word:
            return dictionary_pb2.LookupResponse(found=False, word=word)

        logger.info(f"Dictionary lookup: '{word}'")
        row = _cam_dict.lookup(word)

        if row is None:
            # Return mock entry when DB is unavailable
            return self._mock_lookup(word)

        # Parse pos_items JSON from Cambridge dict
        entries = []
        pos_items = row.get("pos_items")
        if pos_items:
            try:
                items = json.loads(pos_items) if isinstance(pos_items, str) else pos_items
                for item in items:
                    pos = item.get("pos", "")
                    definition = item.get("definition", "")
                    translation = item.get("translation", "")
                    examples = item.get("examples", [])
                    phonetics = item.get("phonetics", {})

                    entries.append(dictionary_pb2.DictEntry(
                        pos=pos,
                        definition=definition,
                        translation=translation,
                        examples=examples if isinstance(examples, list) else [],
                        phonetics_uk=phonetics.get("uk", ""),
                        phonetics_us=phonetics.get("us", ""),
                        audio_uk_url=phonetics.get("audio_uk", ""),
                        audio_us_url=phonetics.get("audio_us", ""),
                    ))
            except (json.JSONDecodeError, TypeError) as e:
                logger.warning(f"Failed to parse pos_items for '{word}': {e}")

        if not entries:
            entries.append(dictionary_pb2.DictEntry(
                pos="",
                definition=row.get("definition", ""),
                translation=row.get("translation", ""),
            ))

        return dictionary_pb2.LookupResponse(
            found=True,
            word=word,
            entries=entries,
        )

    @staticmethod
    def _mock_lookup(word: str) -> dictionary_pb2.LookupResponse:
        """Deterministic mock when database is unavailable."""
        mock_entries = {
            "hello": [
                dictionary_pb2.DictEntry(
                    pos="exclamation",
                    definition="used as a greeting when you meet someone",
                    translation="你好",
                    examples=["Hello, how are you?", "She waved and said hello."],
                    phonetics_uk="/heˈləʊ/",
                    phonetics_us="/heˈloʊ/",
                ),
            ],
            "world": [
                dictionary_pb2.DictEntry(
                    pos="noun",
                    definition="the earth and all the people, places, and things on it",
                    translation="世界",
                    examples=["He travelled the world.", "The world is changing fast."],
                    phonetics_uk="/wɜːld/",
                    phonetics_us="/wɝːld/",
                ),
            ],
        }

        entry = mock_entries.get(word.lower())
        if entry:
            return dictionary_pb2.LookupResponse(found=True, word=word, entries=entry)

        return dictionary_pb2.LookupResponse(found=False, word=word)

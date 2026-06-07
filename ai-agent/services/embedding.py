import os
from typing import List, Dict, Any
from pymilvus import connections, FieldSchema, CollectionSchema, DataType, Collection, utility
from loguru import logger
import numpy as np

MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
COLLECTION_NAME = "st_clip_vectors"
DIMENSION = 1536  # Default for OpenAI ada-002 or Qwen embeddings

class VectorDBClient:
    def __init__(self):
        self.connected = self._connect()
        self.collection = self._init_collection()
        
    def _connect(self):
        try:
            connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
            logger.info(f"Connected to Milvus at {MILVUS_HOST}:{MILVUS_PORT}")
            return True
        except Exception as e:
            logger.warning(f"Milvus unavailable at {MILVUS_HOST}:{MILVUS_PORT}, vector search disabled: {e}")
            return False
            
    def _init_collection(self) -> Collection:
        if not self.connected:
            return None
        try:
            if utility.has_collection(COLLECTION_NAME):
                logger.info(f"Collection {COLLECTION_NAME} already exists.")
                return Collection(COLLECTION_NAME)
                
            fields = [
                FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
                FieldSchema(name="user_id", dtype=DataType.VARCHAR, max_length=128),
                FieldSchema(name="clip_id", dtype=DataType.VARCHAR, max_length=128),
                FieldSchema(name="text", dtype=DataType.VARCHAR, max_length=2048),
                FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=DIMENSION)
            ]
            
            schema = CollectionSchema(fields, description="SilverTongue Clip Vectors")
            collection = Collection(name=COLLECTION_NAME, schema=schema)
            
            # Create IVF_FLAT index
            index_params = {
                "metric_type": "L2",
                "index_type": "IVF_FLAT",
                "params": {"nlist": 128}
            }
            collection.create_index(field_name="embedding", index_params=index_params)
            logger.info(f"Created collection {COLLECTION_NAME} and index.")
            return collection
        except Exception as e:
             logger.warning(f"Milvus collection init skipped, vector search disabled: {e}")
             return None
        
    def mock_embed(self, text: str) -> List[float]:
        """
        Mock embedding generation. In production, call OpenAI or Qwen API.
        """
        # Random vector normalized to length 1
        vec = np.random.rand(DIMENSION)
        vec = vec / np.linalg.norm(vec)
        return vec.tolist()

    def insert(self, user_id: str, clip_id: str, text: str):
        if not self.collection:
            return
        embedding = self.mock_embed(text)
        data = [
            [user_id],
            [clip_id],
            [text],
            [embedding]
        ]
        self.collection.insert(data)
        self.collection.flush()
        
    def search(self, user_id: str, query_text: str, top_k: int = 3) -> List[Dict[str, Any]]:
        if not self.collection:
            return []
        self.collection.load()
        query_vector = self.mock_embed(query_text)
        
        search_params = {
            "metric_type": "L2",
            "params": {"nprobe": 10}
        }
        
        results = self.collection.search(
            data=[query_vector],
            anns_field="embedding",
            param=search_params,
            limit=top_k,
            expr=f"user_id == '{user_id}'",
            output_fields=["text", "clip_id"]
        )
        
        retrieved = []
        for hits in results:
            for hit in hits:
                retrieved.append({
                    "clip_id": hit.entity.get("clip_id"),
                    "text": hit.entity.get("text"),
                    "distance": hit.distance
                })
                
        return retrieved

import os
from dataclasses import dataclass
from functools import lru_cache
from typing import Optional


DEFAULT_MODEL_VERSION = "hedonic-skeleton-v1"
DEFAULT_FEATURE_SET_VERSION = "apartment-basic-skeleton-v1"


@dataclass(frozen=True)
class Settings:
    internal_token: str
    model_version: str
    model_baseline_date: Optional[str]
    feature_set_version: str
    openai_api_key: str
    openai_base_url: str
    rag_docs_dir: str
    rag_chroma_path: str
    rag_collection_name: str
    rag_embedding_model: str
    rag_reranker_model: str
    rag_chunk_size: int
    rag_chunk_overlap: int
    rag_top_k_initial: int
    rag_top_k_final: int


@lru_cache
def get_settings() -> Settings:
    return Settings(
        internal_token=os.getenv("MODEL_SERVER_INTERNAL_TOKEN", "").strip(),
        model_version=os.getenv("MODEL_VERSION", "").strip() or DEFAULT_MODEL_VERSION,
        model_baseline_date=os.getenv("MODEL_BASELINE_DATE", "").strip() or None,
        feature_set_version=(
            os.getenv("MODEL_FEATURE_SET_VERSION", "").strip()
            or DEFAULT_FEATURE_SET_VERSION
        ),
        openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
        openai_base_url=os.getenv("OPENAI_BASE_URL", "").strip(),
        rag_docs_dir=os.getenv("RAG_DOCS_DIR", "").strip(),
        rag_chroma_path=os.getenv("RAG_CHROMA_PATH", "./rag_chroma_db").strip(),
        rag_collection_name=os.getenv("RAG_COLLECTION_NAME", "jiber_rag_e2").strip(),
        rag_embedding_model=os.getenv("RAG_EMBEDDING_MODEL", "BAAI/bge-m3").strip(),
        rag_reranker_model=os.getenv("RAG_RERANKER_MODEL", "BAAI/bge-reranker-base").strip(),
        rag_chunk_size=int(os.getenv("RAG_CHUNK_SIZE", "500")),
        rag_chunk_overlap=int(os.getenv("RAG_CHUNK_OVERLAP", "100")),
        rag_top_k_initial=int(os.getenv("RAG_TOP_K_INITIAL", "10")),
        rag_top_k_final=int(os.getenv("RAG_TOP_K_FINAL", "3")),
    )

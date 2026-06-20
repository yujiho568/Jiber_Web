from __future__ import annotations

import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import chromadb
from openai import OpenAI
from pypdf import PdfReader
from rank_bm25 import BM25Okapi
from sentence_transformers import CrossEncoder, SentenceTransformer

from app.core.config import Settings, get_settings
from app.schemas.chat import ChatContext, RagConfig, RealEstateChatResponse


BM25_WEIGHT = 0.5
SKIP_EXTENSIONS = {".html", ".xlsx", ".xls", ".csv", ".json", ".ds_store"}


@dataclass(frozen=True)
class ChunkRecord:
    text: str
    source: str


class RealEstateRagChatService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.docs_dir = self._resolve_docs_dir(settings)
        self.embedder = SentenceTransformer(settings.rag_embedding_model)
        self.reranker = CrossEncoder(settings.rag_reranker_model)
        self.openai_client = self._openai_client(settings)
        self.collection, self.chunks = self._load_or_build_collection(settings)
        self.bm25 = BM25Okapi([record.text.split() for record in self.chunks])

    def answer(self, question: str, runtime_context: dict | None) -> RealEstateChatResponse:
        retrieval_question = self._question_with_runtime_context(question, runtime_context)
        contexts = self._retrieve(retrieval_question)
        answer = self._llm_answer(question, runtime_context, contexts)
        return RealEstateChatResponse(
            answer=answer,
            contexts=[ChatContext(source=context.source, text=context.text) for context in contexts],
            model="gpt-4o-mini",
            ragConfig=RagConfig(
                embedding=self.settings.rag_embedding_model,
                chunkSize=self.settings.rag_chunk_size,
                overlap=self.settings.rag_chunk_overlap,
                hybrid=True,
                rerank=True,
            ),
        )

    def _retrieve(self, query: str) -> list[ChunkRecord]:
        n = min(self.settings.rag_top_k_initial, self.collection.count())
        query_embedding = self.embedder.encode([query], normalize_embeddings=True).tolist()
        dense_result = self.collection.query(
            query_embeddings=query_embedding,
            n_results=n,
            include=["documents", "metadatas"],
        )
        dense_docs = [
            ChunkRecord(text=document, source=metadata.get("source", "unknown"))
            for document, metadata in zip(
                dense_result["documents"][0],
                dense_result["metadatas"][0],
            )
        ]

        sparse_scores = self.bm25.get_scores(query.split())
        sparse_indexes = sorted(range(len(sparse_scores)), key=lambda index: sparse_scores[index], reverse=True)[:n]
        sparse_docs = [self.chunks[index] for index in sparse_indexes]
        candidates = self._rrf_fusion(dense_docs, sparse_docs)

        pairs = [(query, candidate.text) for candidate in candidates]
        scores = self.reranker.predict(pairs)
        ranked = sorted(zip(candidates, scores), key=lambda item: item[1], reverse=True)
        return [record for record, _ in ranked[: self.settings.rag_top_k_final]]

    def _llm_answer(
        self,
        question: str,
        runtime_context: dict | None,
        contexts: list[ChunkRecord],
    ) -> str:
        context_text = "\n\n".join(
            f"[문서 {index + 1}: {context.source}]\n{context.text}"
            for index, context in enumerate(contexts)
        )
        runtime_text = (
            json.dumps(runtime_context, ensure_ascii=False, indent=2)
            if runtime_context
            else "없음"
        )
        prompt = (
            "너는 부동산 문서 기반 RAG 챗봇이다. "
            "아래 문서와 런타임 컨텍스트에 근거해서만 답한다. "
            "근거가 없거나 부동산/가격예측/XAI와 무관한 질문이면 "
            "'문서에서 찾을 수 없습니다'라고 답한다. "
            "투자 조언, 매수/매도 추천, 수익 보장은 하지 않는다.\n\n"
            f"[런타임 컨텍스트]\n{runtime_text}\n\n"
            f"[검색 문서]\n{context_text}\n\n"
            f"질문: {question}"
        )
        response = self.openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": prompt}],
            temperature=0,
        )
        return response.choices[0].message.content or ""

    def _load_or_build_collection(self, settings: Settings):
        client = chromadb.PersistentClient(path=settings.rag_chroma_path)
        collection_name = settings.rag_collection_name
        expected_metadata = self._collection_metadata(settings)
        try:
            collection = client.get_collection(collection_name)
            current_metadata = collection.metadata or {}
            if current_metadata.get("rag_config") == expected_metadata["rag_config"]:
                return collection, self._load_chunks_from_collection(collection)
            client.delete_collection(collection_name)
        except Exception:
            pass

        records = self._load_chunk_records(settings)
        collection = client.create_collection(
            collection_name,
            metadata=expected_metadata,
        )
        embeddings = self.embedder.encode(
            [record.text for record in records],
            normalize_embeddings=True,
        ).tolist()
        collection.add(
            documents=[record.text for record in records],
            embeddings=embeddings,
            ids=[f"chunk-{index}" for index in range(len(records))],
            metadatas=[{"source": record.source, "order": index} for index, record in enumerate(records)],
        )
        return collection, records

    def _load_chunks_from_collection(self, collection) -> list[ChunkRecord]:
        result = collection.get(include=["documents", "metadatas"])
        rows = sorted(
            zip(result["documents"], result["metadatas"]),
            key=lambda item: int(item[1].get("order", 0)),
        )
        return [
            ChunkRecord(text=document, source=metadata.get("source", "unknown"))
            for document, metadata in rows
        ]

    def _load_chunk_records(self, settings: Settings) -> list[ChunkRecord]:
        documents = self._load_documents(self.docs_dir)
        records: list[ChunkRecord] = []
        for source, content in documents:
            for chunk in self._chunk_text(content, settings.rag_chunk_size, settings.rag_chunk_overlap):
                records.append(ChunkRecord(text=chunk, source=source))
        if not records:
            raise RuntimeError(f"No RAG documents found in {self.docs_dir}")
        return records

    def _load_documents(self, docs_dir: Path) -> list[tuple[str, str]]:
        documents: list[tuple[str, str]] = []
        for path in sorted(docs_dir.rglob("*")):
            if not path.is_file() or path.name.startswith("."):
                continue
            extension = path.suffix.lower()
            if extension in SKIP_EXTENSIONS:
                continue
            relative_path = str(path.relative_to(docs_dir))
            if extension in {".txt", ".md"}:
                content = path.read_text(encoding="utf-8").strip()
                if len(content) >= 50:
                    documents.append((relative_path, content))
            elif extension == ".pdf":
                reader = PdfReader(str(path))
                text = "\n".join(page.extract_text() or "" for page in reader.pages).strip()
                if text:
                    documents.append((relative_path, text))
        return documents

    def _chunk_text(self, text: str, size: int, overlap: int) -> list[str]:
        chunks: list[str] = []
        start = 0
        while start < len(text):
            chunks.append(text[start : start + size])
            start += size - overlap
        return chunks

    def _rrf_fusion(self, dense_docs: list[ChunkRecord], sparse_docs: list[ChunkRecord]) -> list[ChunkRecord]:
        scores: dict[str, float] = {}
        records: dict[str, ChunkRecord] = {}
        for rank, record in enumerate(dense_docs):
            key = self._doc_key(record)
            records[key] = record
            scores[key] = scores.get(key, 0.0) + (1 - BM25_WEIGHT) / (60 + rank + 1)
        for rank, record in enumerate(sparse_docs):
            key = self._doc_key(record)
            records[key] = record
            scores[key] = scores.get(key, 0.0) + BM25_WEIGHT / (60 + rank + 1)
        return [records[key] for key in sorted(scores, key=lambda key: scores[key], reverse=True)]

    def _doc_key(self, record: ChunkRecord) -> str:
        return f"{record.source}:{record.text[:80]}"

    def _question_with_runtime_context(self, question: str, runtime_context: dict | None) -> str:
        if not runtime_context:
            return question
        return f"{question}\n\n[런타임 예측/XAI 컨텍스트]\n{json.dumps(runtime_context, ensure_ascii=False)}"

    def _resolve_docs_dir(self, settings: Settings) -> Path:
        if settings.rag_docs_dir:
            return Path(settings.rag_docs_dir).expanduser().resolve()
        project_root = Path(__file__).resolve().parents[3]
        sibling_docs = project_root.parent / "rag-practice" / "documents"
        if sibling_docs.exists():
            return sibling_docs.resolve()
        return (project_root / "documents").resolve()

    def _collection_metadata(self, settings: Settings) -> dict[str, str]:
        return {
            "hnsw:space": "cosine",
            "rag_config": json.dumps(
                {
                    "embedding": settings.rag_embedding_model,
                    "chunkSize": settings.rag_chunk_size,
                    "overlap": settings.rag_chunk_overlap,
                    "docsDir": str(self.docs_dir),
                },
                ensure_ascii=False,
                sort_keys=True,
            ),
        }

    def _openai_client(self, settings: Settings) -> OpenAI:
        kwargs = {}
        if settings.openai_api_key:
            kwargs["api_key"] = settings.openai_api_key
        if settings.openai_base_url:
            kwargs["base_url"] = settings.openai_base_url
        return OpenAI(**kwargs)


@lru_cache(maxsize=1)
def get_rag_chat_service() -> RealEstateRagChatService:
    return RealEstateRagChatService(get_settings())

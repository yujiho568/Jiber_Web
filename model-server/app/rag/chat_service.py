from __future__ import annotations

from functools import lru_cache

from app.core.config import Settings, get_settings
from app.schemas.chat import ChatContext, RagConfig, RealEstateChatResponse
from app.schemas.chat import RealEstateChatRetrievalResponse


BM25_WEIGHT = 0.5
SKIP_EXTENSIONS = {".html", ".xlsx", ".xls", ".csv", ".json", ".ds_store"}
LOADER_VERSION = "source-summary-v1"

SOURCE_SUMMARIES = {
    "real_estate_sources/r_one_2026_05_housing_price_trend_report.pdf": (
        "문서 제목: 2026년 5월 전국주택가격동향조사 보고서.\n"
        "문서 성격: 한국부동산원 R-ONE의 월간 주택 가격 동향 리포트.\n"
        "조사대상기간: 2026년 5월 1일 ~ 2026년 5월 31일. 공표일: 2026년 6월 15일.\n"
        "조사결과 요약: 2026년 5월 전국 주택가격은 지난달 대비 상승했다.\n"
        "전월대비 변동률은 전국 기준 매매가격지수 0.21%, 전세가격지수 0.35%, 월세통합가격지수 0.35%이다.\n"
        "수도권은 매매 0.46%, 전세 0.61%, 월세통합 0.56%로 전국보다 상승폭이 컸다.\n"
        "지방은 매매 -0.02%, 전세 0.10%, 월세통합 0.16%로 매매는 소폭 하락했고 임대차 지표는 상승했다.\n"
        "서울은 매매 0.90%, 전세 0.91%, 월세통합 0.81%로 상승폭이 컸다.\n"
        "보고서 설명에 따르면 서울은 정주여건 양호 단지, 대단지, 역세권 중심으로 매수·임차 문의가 증가하며 상승했다."
    ),
    "real_estate_sources/r_one_2026_04_apartment_actual_transaction_price_index_report.pdf": (
        "문서 제목: 2026년 4월 공동주택 실거래가격지수 보고서.\n"
        "문서 성격: 한국부동산원 R-ONE의 공동주택 실거래가격지수 리포트.\n"
        "아파트, 연립·다세대 등 공동주택 실거래가격지수와 지역별·규모별 가격지수 변동을 설명한다."
    ),
    "real_estate_sources/r_one_2026_05_officetel_price_trend_report.pdf": (
        "문서 제목: 2026년 5월 오피스텔가격동향조사 보고서.\n"
        "문서 성격: 한국부동산원 R-ONE의 월간 오피스텔 매매·전세·월세 가격 동향 리포트.\n"
        "오피스텔 가격 동향과 아파트 가격 동향 비교 정보를 포함한다."
    ),
}
from app.schemas.chat import RagConfig, RealEstateChatResponse


SKELETON_ANSWER = (
    "부동산 챗봇은 현재 계약 skeleton 단계입니다. "
    "실제 RAG corpus, vector index, embedding model, reranker, LLM provider는 사용자 구현 후 연결됩니다. "
    "현 단계에서는 투자 조언, 법률·세무 판단, 매수·매도 추천을 제공하지 않습니다."
)


class RealEstateChatService:
    def __init__(self, settings: Settings):
        self.settings = settings

    def answer(self, question: str, runtime_context: dict | None) -> RealEstateChatResponse:
        retrieval = self.retrieve(question, runtime_context)
        contexts = [ChunkRecord(text=context.text, source=context.source) for context in retrieval.contexts]
        answer = self._llm_answer(question, runtime_context, contexts)
        return RealEstateChatResponse(
            answer=answer,
            contexts=retrieval.contexts,
            model="gpt-4o-mini",
            ragConfig=retrieval.ragConfig,
        )

    def retrieve(self, question: str, runtime_context: dict | None) -> RealEstateChatRetrievalResponse:
        retrieval_question = self._question_with_runtime_context(question, runtime_context)
        contexts = self._retrieve(retrieval_question)
        return RealEstateChatRetrievalResponse(
            contexts=[ChatContext(source=context.source, text=context.text) for context in contexts],
            ragConfig=self._rag_config(),
        )

    def _retrieve(self, query: str) -> list[ChunkRecord]:
        if not self.chunks or self.collection.count() == 0:
            return []
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
        if not candidates:
            return []

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
            "질문이 주택, 부동산 거래, 임대차, 계약, 권리관계, 가격, 시세, 통계, 실거래, "
            "입지, 건물, 주거 제도, 부동산 서비스, 가격예측, SHAP, XAI 중 하나라도 "
            "합리적으로 연결되면 부동산 관련 질문으로 간주하고 답한다. "
            "개념 설명, 용어 비교, 절차 안내처럼 검색 문서에 직접 문장이 없더라도 "
            "부동산 도메인에 속하면 일반적인 부동산 지식에 기반해 참고용으로 답한다. "
            "일반적인 부동산 문서 질문은 검색 문서를 주요 근거로 답하고, "
            "런타임 컨텍스트가 '없음'이라는 이유만으로 정보가 없다고 말하지 않는다. "
            "현재 매물의 가격예측, 추정가, SHAP, XAI, 영향 요인에 관한 질문은 "
            "런타임 컨텍스트를 주요 근거로 삼아 답한다. "
            "런타임 컨텍스트에 property, valuation, shap 값이 있으면 해당 값들을 직접 해석하고, "
            "검색 문서는 일반적인 부동산 배경 근거로만 보조 사용한다. "
            "검색 문서에 질문과 관련된 수치나 설명이 있으면 먼저 그 내용을 요약한다. "
            "표 일부만 검색되어 단위나 항목명이 불명확한 숫자는 임의로 달러, 원, 만원 등으로 변환하지 말고 "
            "문서에 명확히 보이는 단위와 항목만 사용한다. "
            "검색 문서와 런타임 컨텍스트 어디에도 직접 근거가 없으면, "
            "그 사실을 짧게 밝힌 뒤 일반적인 부동산 지식에 기반한 참고용 답변을 제공한다. "
            "음식, 게임, 연예, 코딩, 일반 잡담처럼 부동산과 명백히 무관한 질문에만 "
            "부동산 관련 질문만 답할 수 있다고 말한다. "
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

    def _rag_config(self) -> RagConfig:
        return RagConfig(
            embedding=self.settings.rag_embedding_model,
            chunkSize=self.settings.rag_chunk_size,
            overlap=self.settings.rag_chunk_overlap,
            hybrid=True,
            rerank=True,
        )

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
        if not records:
            return collection, records
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
            return []
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
                    summary = SOURCE_SUMMARIES.get(relative_path)
                    if summary:
                        documents.append((f"{relative_path}#summary", summary))
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
                    "loaderVersion": LOADER_VERSION,
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

        return RealEstateChatResponse(
            available=False,
            answer=SKELETON_ANSWER,
            contexts=[],
            model="chat-skeleton-v1",
            ragConfig=RagConfig(
                embedding="disabled",
                chunkSize=0,
                overlap=0,
                hybrid=False,
                rerank=False,
            ),
        )


@lru_cache(maxsize=1)
def get_rag_chat_service() -> RealEstateChatService:
    return RealEstateChatService(get_settings())

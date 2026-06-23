from __future__ import annotations

from functools import lru_cache

from app.core.config import Settings, get_settings
from app.schemas.chat import (
    RagConfig,
    RealEstateChatResponse,
    RealEstateChatRetrievalResponse,
)


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
        return RealEstateChatResponse(
            available=False,
            answer=SKELETON_ANSWER,
            contexts=retrieval.contexts,
            model="chat-skeleton-v1",
            ragConfig=retrieval.ragConfig,
        )

    def retrieve(self, question: str, runtime_context: dict | None) -> RealEstateChatRetrievalResponse:
        return RealEstateChatRetrievalResponse(
            contexts=[],
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

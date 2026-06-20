from pydantic import BaseModel, Field
from typing import Optional


class RealEstateChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=1000)
    runtimeContext: Optional[dict] = None


class ChatContext(BaseModel):
    source: str
    text: str


class RagConfig(BaseModel):
    embedding: str
    chunkSize: int
    overlap: int
    hybrid: bool
    rerank: bool


class RealEstateChatResponse(BaseModel):
    answer: str
    contexts: list[ChatContext]
    model: str
    ragConfig: RagConfig

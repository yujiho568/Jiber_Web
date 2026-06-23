# Chat API Contract

## Scope

This contract defines the chat skeleton boundary for the Jiber MVP. It intentionally does not lock in a real RAG pipeline, LLM provider, embedding model, reranker, vector database, prompt strategy, or corpus format.

The user will implement the real regression model, SHAP logic, and RAG/chatbot model later. Until then, chat endpoints may return only a deterministic skeleton response or a `CHATBOT_UNAVAILABLE` public error.

## Service Boundary

- Vue frontend calls only Spring Boot public routes under `/api/v1/**`.
- Vue frontend must not call model-server `/internal/v1/**`, external LLM APIs, OpenAI-compatible APIs, vector stores, or corpus storage directly.
- Spring Boot is the only service that calls FastAPI model-server internal routes under `/internal/v1/**`.
- FastAPI model-server does not own OAuth2/JWT user authentication. It may validate only the backend-to-model-server internal bearer token when configured.
- Raw corpus files, PDFs, XLSX files, generated HTML/Markdown, vector indexes, DB dumps, caches, and model artifacts are local/external artifacts and must not be committed to git.

## Public Endpoint

`POST /api/v1/chat/real-estate`

MVP auth policy: `USER` or `ADMIN` authentication is required.

Rationale: chat requests may contain property context, valuation output, SHAP values, or user-entered questions. Keeping the public route authenticated avoids exposing future model usage and keeps the contract aligned with the authenticated valuation/XAI workflow.

Spring Boot responsibilities:

- Authenticate and authorize the caller.
- Validate request shape.
- Forward only safe contract fields to model-server.
- Convert model-server connection failures to `CHATBOT_UNAVAILABLE`.
- Avoid logging tokens, secrets, raw credentials, or provider responses.

Request:

```json
{
  "question": "전세 계약 전에 무엇을 확인해야 하나요?",
  "runtimeContext": {
    "source": "property-detail",
    "property": {
      "propertyId": 1912,
      "name": "경희궁롯데캐슬",
      "propertyType": "APARTMENT"
    },
    "valuation": {
      "estimatedPrice": 1100000000,
      "currency": "KRW"
    },
    "shap": {
      "values": []
    }
  }
}
```

Validation:

- `question`: required, non-blank, max 1000 characters.
- `runtimeContext`: optional object. It is a transient request payload, not browser persistent storage.

Response:

```json
{
  "available": false,
  "answer": "부동산 챗봇은 현재 계약 skeleton 단계입니다. 실제 RAG corpus, vector index, embedding model, reranker, LLM provider는 사용자 구현 후 연결됩니다. 현 단계에서는 투자 조언, 법률·세무 판단, 매수·매도 추천을 제공하지 않습니다.",
  "contexts": [],
  "model": "chat-skeleton-v1",
  "ragConfig": {
    "embedding": "disabled",
    "chunkSize": 0,
    "overlap": 0,
    "hybrid": false,
    "rerank": false
  }
}
```

Skeleton response rules:

- `available` must be `false` until the real user-owned RAG/model implementation is connected.
- `contexts` must be empty unless a real retrieval pipeline is implemented later.
- `model` must identify the skeleton, not a concrete external LLM.
- `ragConfig.embedding` must be `disabled` in the skeleton.
- The response must not provide investment advice, buy/sell recommendations, guaranteed returns, legal conclusions, tax conclusions, or definitive contract-risk judgments.

## Internal Endpoint

`POST /internal/v1/chat/real-estate`

Caller: Spring Boot backend only.

Auth policy: if `MODEL_SERVER_INTERNAL_TOKEN` is configured, model-server requires `Authorization: Bearer <token>`. The token value must be provided through environment/config only and must not be logged or committed.

The internal request and response shapes mirror the public envelope for the skeleton phase. Future real RAG implementation may expand internal-only diagnostics, but Spring Boot must keep public responses stable and safe.

## Error Mapping

Public Spring Boot error:

```json
{
  "code": "CHATBOT_UNAVAILABLE",
  "message": "부동산 챗봇 서버와 연결할 수 없습니다.",
  "path": "/api/v1/chat/real-estate",
  "timestamp": "2026-06-22T23:30:00+09:00"
}
```

Use `CHATBOT_UNAVAILABLE` when:

- Spring Boot cannot reach model-server.
- model-server returns an invalid/empty response.
- the chat feature is disabled by deployment configuration.
- a future provider/corpus layer is unavailable and cannot produce a safe skeleton response.

## Future Implementation Notes

The real RAG/chatbot implementation should be connected behind this contract after the user provides the model/corpus pipeline. That future work may add artifact configuration, corpus ingestion, vector indexes, provider clients, prompt policies, and model metadata, but those choices are intentionally out of scope for this PR.

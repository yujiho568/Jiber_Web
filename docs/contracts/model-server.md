# Model Server Contract Draft

## Scope

The FastAPI model server provides apartment-only hedonic price valuation and SHAP explanations. It is an internal service called by the Spring Boot backend, not by the Vue frontend.

Internal base path: `/internal/v1`

Public valuation and SHAP routes are documented in `docs/contracts/property-api.md`. The model server contract in this file is internal only.
The chat skeleton public/internal boundary is documented in `docs/contracts/chat-api.md`.

## Service Boundary

- Spring Boot validates public requests and property eligibility.
- Spring Boot maps property and transaction data into model features.
- FastAPI returns estimates, explanation values, model metadata, and missing-data diagnostics.
- FastAPI chat returns only a deterministic unavailable skeleton until the user-owned RAG/model implementation is connected.
- The model server does not perform OAuth2 or user authorization.

## Valuation Endpoint

`POST /internal/v1/valuation/apartments`

Draft request:

```json
{
  "propertyId": 1001,
  "asOfDate": "2026-06-12",
  "features": {
    "sido": "서울특별시",
    "sigungu": "강남구",
    "legalDong": "예시동",
    "exclusiveAreaM2": 84.95,
    "floor": 15,
    "builtYear": 2010,
    "dealYear": 2026,
    "dealMonth": 6,
    "distanceToStationM": 420
  }
}
```

Draft response:

```json
{
  "supported": true,
  "estimatedPrice": 1230000000,
  "currency": "KRW",
  "predictionInterval": {
    "lower": 1150000000,
    "upper": 1310000000
  },
  "modelVersion": "hedonic-v1",
  "baselineDate": "2026-06-12",
  "featureSetVersion": "apartment-basic-v1",
  "warnings": []
}
```

## SHAP Endpoint

`POST /internal/v1/shap/apartments`

Draft response:

```json
{
  "supported": true,
  "baseValue": 980000000,
  "prediction": 1230000000,
  "currency": "KRW",
  "values": [
    {
      "feature": "exclusiveAreaM2",
      "labelKo": "전용면적",
      "value": 84.95,
      "shapValue": 120000000,
      "direction": "UP"
    }
  ],
  "modelVersion": "hedonic-v1",
  "baselineDate": "2026-06-12",
  "featureSetVersion": "apartment-basic-v1"
}
```

## Unsupported / Missing Data

Non-apartment property types should not call the model server unless a future contract expands support. The backend should return `VALUATION_UNSUPPORTED_PROPERTY_TYPE` with a Korean message such as "아파트 단지에 한해 제공되는 기능입니다."

When apartment feature data is incomplete, the model server may return:

```json
{
  "supported": false,
  "reason": "INSUFFICIENT_DATA",
  "missingFeatures": ["exclusiveAreaM2", "builtYear"],
  "modelVersion": "hedonic-v1",
  "baselineDate": "2026-06-12",
  "featureSetVersion": "apartment-basic-v1"
}
```

## Chat Endpoint

`POST /internal/v1/chat/real-estate`

The chat endpoint is a contract skeleton in this PR. It must not require OpenAI keys, provider tokens, ChromaDB, SentenceTransformer, CrossEncoder, vector indexes, PDF/XLSX files, or committed corpus files.

Skeleton response:

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

## Product Guardrails

- Results are estimates and explanations, not appraisal reports or investment advice.
- Responses must not include buy/sell recommendations, investment rankings, guaranteed returns, or language that implies a transaction decision.

# Jiber Model Server

FastAPI 기반 Phase 1 모델 서버 skeleton입니다. 아파트 전용 valuation과 SHAP 설명 internal API 형태를 검증하기 위한 서비스이며, 실제 모델 artifact를 로드하지 않습니다.

이 서버의 결과는 실거래 데이터 기반 추정/설명 API의 계약 확인용입니다. 감정평가서, 투자 조언, 매수/매도 판단, 수익률 보장, 특정 부동산 추천이 아닙니다.

## Dependency 방식

`requirements.txt`를 사용합니다.

- Phase 1 skeleton은 Python 패키지 배포보다 로컬 실행과 테스트 검증이 우선입니다.
- `pip install -r requirements.txt`만으로 FastAPI, Pydantic, uvicorn, pytest, httpx를 바로 설치할 수 있습니다.
- Chat skeleton은 OpenAI client, ChromaDB, SentenceTransformer, CrossEncoder, PDF parser, vector index, corpus 파일을 필요로 하지 않습니다.
- `setup.py`는 dependency 관리가 아니라 repo root에서도 `app.main`을 import할 수 있게 하는 editable package metadata입니다.
- 패키징, formatter, linter, model artifact 관리가 추가되면 이후 `pyproject.toml`로 전환할 수 있습니다.

## 실행 방법

```bash
cd model-server
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m pip install -e .
.venv/bin/python -m uvicorn app.main:app --reload --port 8000
```

health check:

```bash
curl http://localhost:8000/health
```

## 테스트 방법

```bash
cd model-server
.venv/bin/python -m pytest
```

FastAPI app import 확인:

```bash
cd model-server
.venv/bin/python -c "from app.main import app; print(app.title)"
```

repo root 기준 검증:

```bash
model-server/.venv/bin/python -m pytest -q
model-server/.venv/bin/python -c "from app.main import app; print(app.title)"
```

root 기준 import는 `.venv/bin/python -m pip install -e .`가 만든 editable package link를 사용합니다. 이 설정은 `model-server/app`을 site-packages에 복사하지 않고 현재 작업tree를 직접 참조하므로, skeleton 개발 중 변경사항이 바로 테스트에 반영됩니다.

## Environment Variables

루트 `.env.example`의 model-server 관련 변수를 사용합니다. 실제 secret 값은 커밋하지 않습니다.

| Name | Required | Description |
| --- | --- | --- |
| `MODEL_SERVER_INTERNAL_TOKEN` | no | 비어 있으면 local skeleton 모드로 header 검증을 생략합니다. 값이 있으면 `Authorization: Bearer <token>` header가 필요합니다. |
| `MODEL_VERSION` | no | 비어 있으면 `hedonic-skeleton-v1`을 사용합니다. |
| `MODEL_BASELINE_DATE` | no | 비어 있으면 요청의 `asOfDate`를 응답 `baselineDate`로 사용합니다. |
| `MODEL_FEATURE_SET_VERSION` | no | 비어 있으면 `apartment-basic-skeleton-v1`을 사용합니다. |

## Internal API

Spring Boot backend만 이 서버를 호출해야 합니다. Vue frontend는 이 서버를 직접 호출하지 않습니다.

- `GET /health`
- `POST /internal/v1/valuation/apartments`
- `POST /internal/v1/shap/apartments`
- `POST /internal/v1/chat/real-estate`

public valuation과 SHAP route는 Spring Boot의 `/api/v1/properties/{propertyId}/valuation`, `/api/v1/properties/{propertyId}/shap`입니다. 사용자 인증, 권한, public error response 변환은 Spring Boot가 담당합니다.
public chat route 후보는 Spring Boot의 `/api/v1/chat/real-estate`이며, MVP skeleton에서는 `USER` 또는 `ADMIN` 인증을 요구합니다. Vue frontend는 model-server를 직접 호출하지 않습니다.

## Chat Skeleton 동작

현재 chat endpoint는 contract 검증용 skeleton입니다. 실제 RAG corpus, vector index, embedding model, reranker, LLM provider, prompt policy는 사용자가 구현한 뒤 연결합니다.

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

Local-only corpus or generated indexes may be placed under ignored artifact/storage paths such as `model-server/documents/`, `model-server/artifacts/`, `model-server/cache/`, or `model-server/vector_index/` during user-owned implementation work. Do not commit those files.

## Deterministic Skeleton 동작

실제 모델이 없으므로 deterministic mock 계산을 사용합니다.

```text
baseValue = 500,000,000 KRW
exclusiveAreaM2 contribution = exclusiveAreaM2 * 3,000,000
floor contribution = floor * 800,000
builtYear contribution = max(builtYear - 2000, 0) * 1,500,000
distanceToStationM contribution = distanceToStationM * -10,000
prediction = baseValue + all contributions
predictionInterval = prediction +/- 8%
```

SHAP 응답의 `values`는 위 contribution을 feature별 mock SHAP 값으로 그대로 반환합니다. 이는 Phase 1 skeleton 설명값이며 실제 SHAP 계산이 아닙니다.

## Missing Data

현재 skeleton의 필수 feature는 다음과 같습니다.

- `exclusiveAreaM2`
- `builtYear`

필수 feature가 없으면 HTTP error 대신 내부 contract payload로 응답합니다.

```json
{
  "supported": false,
  "reason": "INSUFFICIENT_DATA",
  "missingFeatures": ["exclusiveAreaM2", "builtYear"],
  "modelVersion": "hedonic-skeleton-v1",
  "baselineDate": "2026-06-12",
  "featureSetVersion": "apartment-basic-skeleton-v1"
}
```

public API의 `VALUATION_INSUFFICIENT_DATA` error shape 변환은 Spring Boot backend 책임입니다.

## Scope Notes

- 아파트 전용 internal API입니다.
- 비아파트 unsupported 판단은 기본적으로 Spring Boot public layer에서 처리합니다.
- OAuth2/JWT 사용자 인증은 이 서버가 소유하지 않습니다.
- 실제 모델 artifact, RAG corpus, vector index, 모델 경로, API key, OAuth secret, JWT secret, internal token 값은 저장소에 포함하지 않습니다.

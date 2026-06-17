# Error Response Contract

## Shape

All public Spring Boot API errors should use this shape:

```json
{
  "code": "PROPERTY_NOT_FOUND",
  "message": "요청한 부동산 정보를 찾을 수 없습니다.",
  "details": [
    {
      "field": "propertyId",
      "reason": "존재하지 않는 ID입니다."
    }
  ],
  "path": "/api/v1/properties/123",
  "timestamp": "2026-06-12T10:30:00+09:00"
}
```

## Field Rules

- `code`: Stable machine-readable error code in uppercase snake case.
- `message`: Safe Korean message for the frontend to show or map.
- `details`: Optional validation or integration details. Do not include secrets or stack traces.
- `path`: Request path.
- `timestamp`: ISO 8601 timestamp with timezone offset.

## Initial Error Codes

| HTTP status | Code | Usage |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Request parameter or body validation failed. |
| 401 | `AUTH_REQUIRED` | Login is required. |
| 401 | `INVALID_CREDENTIALS` | Email/password login failed. Do not reveal which field was wrong. |
| 401 | `SOCIAL_PENDING_NOT_FOUND` | Pending social signup/link session is missing, expired, consumed, or invalid. |
| 403 | `ACCESS_DENIED` | User lacks the required role or ownership. |
| 404 | `PROPERTY_NOT_FOUND` | Property does not exist. |
| 404 | `NOTICE_NOT_FOUND` | Notice does not exist. |
| 404 | `FAVORITE_NOT_FOUND` | Favorite apartment does not exist for the current user. |
| 404 | `FAVORITE_AREA_NOT_FOUND` | Favorite area does not exist for the current user. |
| 409 | `FAVORITE_ALREADY_EXISTS` | Favorite already exists for the user. |
| 409 | `FAVORITE_AREA_ALREADY_EXISTS` | Favorite area already exists for the user. |
| 409 | `EMAIL_ALREADY_EXISTS` | Signup email already belongs to a Jiber account. |
| 409 | `SOCIAL_ACCOUNT_ALREADY_LINKED` | OAuth provider identity is already linked to a Jiber account. |
| 422 | `VALUATION_UNSUPPORTED_PROPERTY_TYPE` | AI valuation is not available for non-apartment property types. |
| 422 | `VALUATION_INSUFFICIENT_DATA` | Required model features are missing. |
| 502 | `MODEL_SERVER_UNAVAILABLE` | Backend could not reach the model server. |
| 500 | `INTERNAL_ERROR` | Unexpected server error. |

## Security Notes

- Do not return OAuth provider tokens, JWT signing material, DB credentials, model internal paths, stack traces, or raw SQL errors.
- Authentication failures should be specific enough for UX, but not reveal whether an OAuth account exists beyond what the provider flow already exposes.

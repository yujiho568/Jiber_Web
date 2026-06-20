from fastapi import APIRouter, Depends

from app.core.config import get_settings
from app.core.security import verify_internal_token
from app.schemas.apartment import (
    ApartmentInferenceRequest,
    ShapResponse,
    ValuationResponse,
)
from app.schemas.chat import RealEstateChatRequest, RealEstateChatResponse
from app.rag.chat_service import get_rag_chat_service
from app.services.valuation_service import ApartmentValuationService


router = APIRouter(
    prefix="/internal/v1",
    dependencies=[Depends(verify_internal_token)],
)
service = ApartmentValuationService()


@router.post(
    "/valuation/apartments",
    response_model=ValuationResponse,
)
def valuate_apartment(request: ApartmentInferenceRequest) -> ValuationResponse:
    settings = get_settings()
    missing_features = service.missing_required_features(request.features)
    if missing_features:
        return service.unsupported(request, settings, missing_features)

    return service.valuation(request, settings)


@router.post(
    "/shap/apartments",
    response_model=ShapResponse,
)
def explain_apartment(request: ApartmentInferenceRequest) -> ShapResponse:
    settings = get_settings()
    missing_features = service.missing_required_features(request.features)
    if missing_features:
        return service.unsupported(request, settings, missing_features)

    return service.shap(request, settings)


@router.post(
    "/chat/real-estate",
    response_model=RealEstateChatResponse,
)
def chat_real_estate(request: RealEstateChatRequest) -> RealEstateChatResponse:
    return get_rag_chat_service().answer(request.question, request.runtimeContext)

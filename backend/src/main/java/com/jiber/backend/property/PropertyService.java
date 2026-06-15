package com.jiber.backend.property;

import com.jiber.backend.common.PageMetadata;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PropertyService {

    private final PropertyAiEligibilityService eligibilityService;
    private final PropertyValuationClient valuationClient;

    public PropertyService(PropertyAiEligibilityService eligibilityService, PropertyValuationClient valuationClient) {
        this.eligibilityService = eligibilityService;
        this.valuationClient = valuationClient;
    }

    public PropertyMapResponse findMapProperties(MapSearchRequest request) {
        return new PropertyMapResponse(
                List.of(),
                new BoundsResponse(request.swLat(), request.swLng(), request.neLat(), request.neLng()),
                new MapFilterResponse(request.propertyTypes(), request.transactionTypes(), request.zoomLevel())
        );
    }

    public PropertySearchResponse searchProperties(PropertySearchRequest request) {
        return new PropertySearchResponse(List.of(), PageMetadata.empty(request.effectivePage(), request.effectiveSize()));
    }

    public PropertyDetailResponse getPropertyDetail(Long propertyId) {
        return new PropertyDetailResponse(
                propertyId,
                PropertyType.APARTMENT,
                "샘플 아파트",
                new PropertyDetailResponse.Address("서울특별시", "강남구", "예시동", "서울특별시 강남구 예시로 1"),
                new PropertyDetailResponse.Location(37.5001, 127.0364),
                new PropertyDetailResponse.Summary(2010, 500, 1250000000L, LocalDate.of(2026, 5, 20)),
                List.of(),
                new PropertyDetailResponse.FavoriteSummary(false, false),
                new PropertyDetailResponse.AiMetadata(true, true, null)
        );
    }

    public ValuationResponse valuateApartment(Long propertyId, ValuationRequest request) {
        var propertyType = resolvePropertyTypeForAi(propertyId);
        eligibilityService.ensureApartmentSupported(propertyType);
        return valuationClient.valuateApartment(propertyId, request);
    }

    public ShapResponse explainApartment(Long propertyId, ShapRequest request) {
        var propertyType = resolvePropertyTypeForAi(propertyId);
        eligibilityService.ensureApartmentSupported(propertyType);
        return valuationClient.explainApartment(propertyId, request);
    }

    private PropertyType resolvePropertyTypeForAi(Long propertyId) {
        return PropertyType.APARTMENT;
    }
}

package com.jiber.backend.property;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ModelServerApartmentFeatureMapper {

    private static final String SKELETON_SIDO = "서울특별시";
    private static final String SKELETON_SIGUNGU = "강남구";
    private static final String SKELETON_LEGAL_DONG = "예시동";
    private static final int SKELETON_BUILT_YEAR = 2010;
    private static final BigDecimal SKELETON_DISTANCE_TO_STATION_M = new BigDecimal("420");

    public ModelServerApartmentInferenceRequest toInternalRequest(Long propertyId, ValuationRequest request) {
        return toInternalRequest(propertyId, request.exclusiveAreaM2(), request.floor(), request.asOfDate());
    }

    public ModelServerApartmentInferenceRequest toInternalRequest(Long propertyId, ShapRequest request) {
        return toInternalRequest(propertyId, request.exclusiveAreaM2(), request.floor(), request.asOfDate());
    }

    private ModelServerApartmentInferenceRequest toInternalRequest(
            Long propertyId,
            BigDecimal exclusiveAreaM2,
            Integer floor,
            LocalDate asOfDate
    ) {
        var features = new ModelServerApartmentFeatures(
                SKELETON_SIDO,
                SKELETON_SIGUNGU,
                SKELETON_LEGAL_DONG,
                exclusiveAreaM2,
                floor,
                SKELETON_BUILT_YEAR,
                asOfDate.getYear(),
                asOfDate.getMonthValue(),
                SKELETON_DISTANCE_TO_STATION_M
        );
        return new ModelServerApartmentInferenceRequest(propertyId, asOfDate.toString(), features);
    }
}

package com.jiber.backend.property;

import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkeletonPropertyValuationClient implements PropertyValuationClient {

    private final String modelVersion;
    private final LocalDate baselineDate;
    private final String featureSetVersion;

    public SkeletonPropertyValuationClient(
            @Value("${jiber.model-server.model-version:hedonic-v1}") String modelVersion,
            @Value("${jiber.model-server.baseline-date:2026-06-12}") String baselineDate,
            @Value("${jiber.model-server.feature-set-version:apartment-basic-v1}") String featureSetVersion
    ) {
        this.modelVersion = modelVersion;
        this.baselineDate = LocalDate.parse(baselineDate);
        this.featureSetVersion = featureSetVersion;
    }

    @Override
    public ValuationResponse valuateApartment(Long propertyId, ValuationRequest request) {
        return new ValuationResponse(
                propertyId,
                true,
                0L,
                "KRW",
                new PredictionIntervalResponse(0L, 0L),
                modelVersion,
                baselineDate,
                featureSetVersion,
                "모델 서버 연동 전 스켈레톤 응답입니다."
        );
    }

    @Override
    public ShapResponse explainApartment(Long propertyId, ShapRequest request) {
        return new ShapResponse(
                propertyId,
                true,
                0L,
                0L,
                "KRW",
                List.of(new ShapValueResponse("exclusiveAreaM2", "전용면적", request.exclusiveAreaM2(), 0L, ShapDirection.NEUTRAL)),
                modelVersion,
                baselineDate,
                featureSetVersion,
                "모델 서버 연동 전 스켈레톤 응답입니다."
        );
    }
}

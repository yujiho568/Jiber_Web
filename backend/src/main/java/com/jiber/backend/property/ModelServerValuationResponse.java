package com.jiber.backend.property;

import java.util.List;

public record ModelServerValuationResponse(
        Boolean supported,
        Long estimatedPrice,
        String currency,
        ModelServerPredictionInterval predictionInterval,
        String modelVersion,
        String baselineDate,
        String featureSetVersion,
        List<String> warnings,
        String reason,
        List<String> missingFeatures
) {
}

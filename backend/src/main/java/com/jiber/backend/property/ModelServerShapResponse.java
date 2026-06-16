package com.jiber.backend.property;

import java.util.List;

public record ModelServerShapResponse(
        Boolean supported,
        Long baseValue,
        Long prediction,
        String currency,
        List<ModelServerShapValue> values,
        String modelVersion,
        String baselineDate,
        String featureSetVersion,
        String reason,
        List<String> missingFeatures
) {
}

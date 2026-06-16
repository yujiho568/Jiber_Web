package com.jiber.backend.property;

import java.math.BigDecimal;

public record ModelServerApartmentFeatures(
        String sido,
        String sigungu,
        String legalDong,
        BigDecimal exclusiveAreaM2,
        Integer floor,
        Integer builtYear,
        Integer dealYear,
        Integer dealMonth,
        BigDecimal distanceToStationM
) {
}

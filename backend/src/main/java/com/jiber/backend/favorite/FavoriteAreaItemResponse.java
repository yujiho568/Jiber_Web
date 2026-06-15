package com.jiber.backend.favorite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FavoriteAreaItemResponse(
        Long favoriteAreaId,
        String label,
        String sido,
        String sigungu,
        String legalDong,
        BigDecimal centerLat,
        BigDecimal centerLng,
        Integer zoomLevel,
        OffsetDateTime createdAt
) {
}

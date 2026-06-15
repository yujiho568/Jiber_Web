package com.jiber.backend.favorite;

import java.time.OffsetDateTime;

public record FavoriteAreaCreateResponse(
        Long favoriteAreaId,
        String label,
        OffsetDateTime createdAt,
        String message
) {
}

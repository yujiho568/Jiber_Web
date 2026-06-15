package com.jiber.backend.favorite;

import java.time.OffsetDateTime;

public record FavoriteApartmentCreateResponse(
        Long favoriteId,
        Long propertyId,
        OffsetDateTime createdAt,
        String message
) {
}

package com.jiber.backend.favorite;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FavoriteApartmentCreateRequest(
        @NotNull @Positive Long propertyId
) {
}

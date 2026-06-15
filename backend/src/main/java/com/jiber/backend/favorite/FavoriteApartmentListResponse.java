package com.jiber.backend.favorite;

import java.util.List;

public record FavoriteApartmentListResponse(
        List<FavoriteApartmentItemResponse> items
) {
}

package com.jiber.backend.favorite;

import java.util.List;

public record FavoriteAreaListResponse(
        List<FavoriteAreaItemResponse> items
) {
}

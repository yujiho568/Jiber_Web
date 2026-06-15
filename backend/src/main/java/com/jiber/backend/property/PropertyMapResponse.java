package com.jiber.backend.property;

import java.util.List;

public record PropertyMapResponse(
        List<PropertyMapItemResponse> items,
        BoundsResponse bounds,
        MapFilterResponse filters
) {
}

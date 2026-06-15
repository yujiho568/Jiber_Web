package com.jiber.backend.property;

import com.jiber.backend.common.PageMetadata;
import java.util.List;

public record PropertySearchResponse(
        List<PropertySearchItemResponse> items,
        PageMetadata page
) {
}

package com.jiber.backend.notice;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record NoticeListRequest(
        @PositiveOrZero Integer page,
        @Positive @Max(100) Integer size,
        String sort,
        String keyword,
        Boolean pinnedOnly
) {
    public int effectivePage() {
        return page == null ? 0 : page;
    }

    public int effectiveSize() {
        return size == null ? 20 : size;
    }
}

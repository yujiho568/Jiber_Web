package com.jiber.backend.notice;

import java.time.OffsetDateTime;

public record NoticeSummaryResponse(
        Long noticeId,
        String title,
        String summary,
        boolean pinned,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt
) {
}

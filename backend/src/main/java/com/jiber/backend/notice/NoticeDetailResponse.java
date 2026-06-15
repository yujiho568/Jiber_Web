package com.jiber.backend.notice;

import java.time.OffsetDateTime;

public record NoticeDetailResponse(
        Long noticeId,
        String title,
        String content,
        boolean pinned,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

package com.jiber.backend.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record NoticeUpsertRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotNull Boolean pinned,
        @NotNull OffsetDateTime publishedAt
) {
}

package com.jiber.backend.notice;

public record NoticeMutationResponse(
        Long noticeId,
        String message
) {
}

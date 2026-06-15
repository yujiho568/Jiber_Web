package com.jiber.backend.common.error;

public record ErrorDetail(
        String field,
        String reason
) {
}

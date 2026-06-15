package com.jiber.backend.common.error;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<ErrorDetail> details,
        String path,
        OffsetDateTime timestamp
) {
    public static ApiErrorResponse of(
            ErrorCode code,
            String message,
            List<ErrorDetail> details,
            String path,
            OffsetDateTime timestamp
    ) {
        return new ApiErrorResponse(
                code.name(),
                message,
                details == null ? List.of() : List.copyOf(details),
                path,
                timestamp
        );
    }
}

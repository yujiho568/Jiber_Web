package com.jiber.backend.property;

public record PredictionIntervalResponse(
        Long lower,
        Long upper
) {
}

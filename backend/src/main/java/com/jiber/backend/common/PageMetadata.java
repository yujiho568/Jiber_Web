package com.jiber.backend.common;

public record PageMetadata(
        int number,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageMetadata empty(int number, int size) {
        return new PageMetadata(number, size, 0, 0);
    }
}

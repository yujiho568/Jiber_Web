package com.jiber.backend.chat;

public record RagConfigResponse(
        String embedding,
        int chunkSize,
        int overlap,
        boolean hybrid,
        boolean rerank
) {
}

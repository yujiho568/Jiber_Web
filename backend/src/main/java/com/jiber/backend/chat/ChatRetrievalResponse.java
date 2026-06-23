package com.jiber.backend.chat;

import java.util.List;

public record ChatRetrievalResponse(
        List<ChatContextResponse> contexts,
        RagConfigResponse ragConfig
) {
}

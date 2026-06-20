package com.jiber.backend.chat;

import java.util.List;

public record ChatResponse(
        String answer,
        List<ChatContextResponse> contexts,
        String model,
        RagConfigResponse ragConfig
) {
}

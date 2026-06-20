package com.jiber.backend.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ChatRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 1000, message = "질문은 1000자 이하로 입력해 주세요.")
        String question,
        Map<String, Object> runtimeContext
) {
}

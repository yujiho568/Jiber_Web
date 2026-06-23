package com.jiber.backend.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            당신은 부동산 문서 기반 RAG 챗봇입니다.
            사용자의 질문이 주택, 부동산 거래, 전월세, 가격 시세, 통계, 실거래가,
            가격 예측, SHAP, XAI 중 하나와 합리적으로 연결되면 부동산 관련 질문으로 간주합니다.
            검색 문서와 런타임 컨텍스트를 우선 근거로 사용하고, 근거가 부족하면 그 한계를 명확히 말합니다.
            투자 조언, 매수/매도 추천, 수익 보장은 하지 않습니다.
            답변은 한국어로 작성합니다.
            """;

    private final ModelServerChatClient modelServerChatClient;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;

    public ChatService(
            ModelServerChatClient modelServerChatClient,
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String chatModel
    ) {
        this.modelServerChatClient = modelServerChatClient;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    public ChatResponse ask(ChatRequest request) {
        var retrieval = modelServerChatClient.retrieve(request);
        var answer = generateAnswer(request, retrieval.contexts());
        return new ChatResponse(answer, retrieval.contexts(), chatModel, retrieval.ragConfig());
    }

    private String generateAnswer(ChatRequest request, List<ChatContextResponse> contexts) {
        try {
            var content = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(user -> user.text("""
                            [런타임 컨텍스트]
                            {runtimeContext}

                            [검색 문서]
                            {contexts}

                            [질문]
                            {question}
                            """)
                            .param("runtimeContext", runtimeContextJson(request))
                            .param("contexts", contextText(contexts))
                            .param("question", request.question()))
                    .call()
                    .content();
            return content == null ? "" : content;
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.CHATBOT_UNAVAILABLE);
        }
    }

    private String runtimeContextJson(ChatRequest request) {
        if (request.runtimeContext() == null || request.runtimeContext().isEmpty()) {
            return "없음";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request.runtimeContext());
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private static String contextText(List<ChatContextResponse> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "검색된 문서 없음";
        }
        var builder = new StringBuilder();
        for (int index = 0; index < contexts.size(); index++) {
            var context = contexts.get(index);
            builder.append("[문서 ")
                    .append(index + 1)
                    .append(": ")
                    .append(context.source())
                    .append("]\n")
                    .append(context.text())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }
}

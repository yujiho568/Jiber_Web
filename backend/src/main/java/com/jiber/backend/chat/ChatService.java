package com.jiber.backend.chat;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ModelServerChatClient chatClient;

    public ChatService(ModelServerChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatResponse ask(ChatRequest request) {
        return chatClient.ask(request);
    }
}

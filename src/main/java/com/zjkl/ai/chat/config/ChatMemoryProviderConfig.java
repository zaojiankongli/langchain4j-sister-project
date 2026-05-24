package com.zjkl.ai.chat.config;


import com.zjkl.memory.store.RedisChatMemoryStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryProviderConfig {
    @Bean
    public ChatMemoryProvider redisChatMemoryProvider(RedisChatMemoryStore redisChatMemoryStore){
        return memoryId -> MessageWindowChatMemory.builder()
                        .chatMemoryStore(redisChatMemoryStore)
                        .maxMessages(200)
                        .id(memoryId.toString())
                        .build();
    }
}

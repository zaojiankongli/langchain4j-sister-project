package com.zjkl.emotion.service;

import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.entity.ConverMessage;
import com.zjkl.ai.chat.service.ConverMessageService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatReplyPersistenceService {

    private final ChatMemoryProvider redisChatMemoryProvider;
    private final ConverMessageService converMessageService;

    public void saveChatMemory(String userId, String userInput, String imageUrl,
                               CompletableFuture<String> imageDescFuture, String fullReply) {
        try {
            String redisUserText = userInput;
            if (imageUrl != null && !imageUrl.isBlank() && imageDescFuture != null) {
                try {
                    String imageDesc = imageDescFuture.getNow(null);
                    if (imageDesc != null && !imageDesc.isBlank()) {
                        redisUserText += " [图片:" + imageDesc + "|" + imageUrl + "]";
                        log.debug("VLM 描述已就绪并写入记忆: {}", imageDesc);
                    } else {
                        log.debug("VLM 描述尚未就绪，跳过等待: userId={}", userId);
                    }
                } catch (Exception e) {
                    log.warn("VLM 描述读取失败，使用原始文本: {}", e.getMessage());
                }
            }

            try {
                var chatMemory = redisChatMemoryProvider.get(userId);
                chatMemory.add(UserMessage.from(redisUserText));
                chatMemory.add(AiMessage.from(fullReply));
                log.debug("Redis 记忆已保存: userId={}", userId);
            } catch (Exception e) {
                log.error("Redis 记忆保存失败: userId={}", userId, e);
            }

            LocalDateTime now = LocalDateTime.now();

            List<MessageContent> userContents = new ArrayList<>();
            userContents.add(MessageContent.text(userInput));
            if (imageUrl != null && !imageUrl.isBlank()) {
                userContents.add(MessageContent.image(imageUrl));
            }

            List<MessageContent> aiContents = List.of(MessageContent.text(fullReply));

            converMessageService.batchSaveMessages(List.of(
                    ConverMessage.builder()
                            .id(UUID.randomUUID().toString())
                            .userId(userId)
                            .role("user")
                            .contents(userContents)
                            .createdAt(now)
                            .build(),
                    ConverMessage.builder()
                            .id(UUID.randomUUID().toString())
                            .userId(userId)
                            .role("assistant")
                            .contents(aiContents)
                            .createdAt(now)
                            .build()
            ));

            log.debug("MySQL 消息已保存: userId={}, userContents={}, aiContents={}",
                    userId, userContents.size(), aiContents.size());

        } catch (Exception e) {
            log.error("保存记忆失败: userId={}", userId, e);
        }
    }
}

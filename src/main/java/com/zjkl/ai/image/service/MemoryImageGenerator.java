package com.zjkl.ai.image.service;

import com.zjkl.ai.image.domain.ImageElements;
import com.zjkl.ai.oss.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆图片生成协调器
 *
 * 异步生成图片，支持重试机制，重试耗尽后降级为默认图片
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryImageGenerator {

    private final ImageElementExtractor elementExtractor;
    private final WanxImageService wanxService;
    private final OssService ossService;

    /**
     * 默认图片 URL（生成失败时使用）
     */
    @Value("${app.default-image-url}")
    private String defaultImageUrl;

    /**
     * 异步生成记忆图片（使用 JDK 21 虚拟线程）
     *
     * @param userId 用户 ID
     * @param title 记忆标题
     * @param summary 记忆摘要
     * @param memoryDate 记忆日期
     * @return CompletableFuture<String> 图片 URL
     */
    @Async("imageTaskExecutor")
    public CompletableFuture<String> generateImageAsync(
            String userId,
            String title,
            String summary,
            LocalDate memoryDate) {

        // 最多重试 3 次
        Exception lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("开始生成记忆图片，userId={}, date={}, attempt={}/3", userId, memoryDate, attempt);

                ImageElements elements = elementExtractor.extract(summary);
                log.info("图片元素提取成功：clothing={}, scene={}, atmosphere={}",
                    elements.getClothing(), elements.getScene(), elements.getAtmosphere());

                String tempImageUrl = wanxService.generate(elements);
                log.info("通义万相生成图片成功：{}", tempImageUrl);

                String imageUrl = ossService.uploadFromUrl(tempImageUrl, null);
                log.info("图片已上传 OSS: {}", imageUrl);

                return CompletableFuture.completedFuture(imageUrl);

            } catch (Exception e) {
                lastException = e;
                log.warn("图片生成失败（第{}次）：userId={}", attempt, userId, e);
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000L * (1L << (attempt - 1))); // 2s, 4s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("图片生成重试耗尽，降级为默认图，userId={}, reason={}", userId, lastException.getMessage());
        return CompletableFuture.completedFuture(defaultImageUrl);
    }
}

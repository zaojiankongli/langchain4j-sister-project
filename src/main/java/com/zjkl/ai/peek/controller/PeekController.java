package com.zjkl.ai.peek.controller;

import com.zjkl.ai.oss.service.OssService;
import com.zjkl.ai.peek.service.PeekCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Peek REST 控制器
 *
 * 提供截图上传回调端点，供前端/Electron 客户端调用
 */
@Slf4j
@RestController
@RequestMapping("/api/peek")
@RequiredArgsConstructor
public class PeekController {

    private final StringRedisTemplate redisTemplate;
    private final OssService ossService;
    private final PeekCallbackService peekCallbackService;

    @Value("${peek.screenshot-folder:peek}")
    private String screenshotFolder;

    private static final String PEEK_PENDING_KEY_PREFIX = "peek:pending:";

    /**
     * 截图上传回调
     *
     * 前端/Electron 收到 PEEK_REQUEST 后截图，通过此接口上传
     *
     * @param peekId     peek 任务 ID（PeekScheduler 生成，通过 WebSocket 下发）
     * @param screenshot 截图文件
     * @return 202=已接受处理, 400=参数错误, 404=peekId 无效
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleScreenshotCallback(
            @RequestParam String peekId,
            @RequestParam("screenshot") MultipartFile screenshot) {

        // 1. 校验参数
        if (peekId == null || peekId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "peekId 不能为空"));
        }
        if (screenshot == null || screenshot.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "截图文件不能为空"));
        }

        // 2. 原子消费 peekId（getAndDelete，防止重复上传）
        String redisKey = PEEK_PENDING_KEY_PREFIX + peekId;
        String userId = redisTemplate.opsForValue().getAndDelete(redisKey);

        if (userId == null) {
            log.warn("peek 回调收到无效或已过期的 peekId：{}", peekId);
            return ResponseEntity.status(404).body(Map.of("error", "peekId 无效或已过期"));
        }

        log.info("收到 peek 截图回调：userId={}, peekId={}, fileSize={} bytes",
                userId, peekId, screenshot.getSize());

        String imageUrl = null;
        try {
            // 3. 上传截图到 OSS
            imageUrl = ossService.uploadFile(screenshotFolder, screenshot);
            log.info("peek 截图已上传至 OSS：userId={}, imageUrl={}", userId, imageUrl);

            // 4. 异步处理（VLM → Agent → TTS → 推送）
            peekCallbackService.handlePeekCallback(userId, imageUrl, peekId);

            // 5. 立即返回 202，不等待异步处理完成
            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "peekId", peekId,
                    "message", "截图已接收，正在处理"
            ));

        } catch (Exception e) {
            log.error("peek 截图上传失败：userId={}, peekId={}", userId, peekId, e);
            if (imageUrl != null) {
                try {
                    ossService.deleteFile(imageUrl);
                } catch (Exception ignored) {}
            }
            return ResponseEntity.status(500).body(Map.of("error", "截图上传失败：" + e.getMessage()));
        }
    }
}

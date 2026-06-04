package com.zjkl.ai.image.controller;

import com.zjkl.ai.image.domain.ImageElements;
import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.ai.image.service.ImageElementExtractor;
import com.zjkl.ai.image.service.WanxImageService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 图片服务接口
 */
@Slf4j
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
@Validated
public class ImageController {

    private final ImageDescriptionService imageDescriptionService;
    private final ImageElementExtractor imageElementExtractor;
    private final WanxImageService wanxImageService;
    private final UserContext userContext;

    @Autowired
    @Qualifier("imageTaskExecutor")
    private Executor imageTaskExecutor;

    @GetMapping("/describe")
    public Result<Map<String, String>> describe(@RequestParam @Pattern(regexp = "^https?://.+") String imageUrl) {
        if (userContext.getUserId() == null) {
            return Result.unauthorized("请先登录");
        }
        validateRemoteUrl(imageUrl);
        String description = imageDescriptionService.describe(imageUrl);
        return Result.success(Map.of("imageUrl", imageUrl, "description", description));
    }

    @GetMapping("/describe/peek")
    public Result<Map<String, String>> describeForPeek(@RequestParam @Pattern(regexp = "^https?://.+") String imageUrl) {
        if (userContext.getUserId() == null) {
            return Result.unauthorized("请先登录");
        }
        validateRemoteUrl(imageUrl);
        String description = imageDescriptionService.describeForPeek(imageUrl);
        return Result.success(Map.of("imageUrl", imageUrl, "description", description));
    }

    @PostMapping("/extract-elements")
    public Result<ImageElements> extractElements(@RequestBody Map<String, String> request) {
        if (userContext.getUserId() == null) {
            return Result.unauthorized("请先登录");
        }
        String memoryContent = request.get("memoryContent");
        return Result.success(imageElementExtractor.extract(memoryContent));
    }

    @PostMapping("/generate")
    public CompletableFuture<Result<Map<String, String>>> generateImage(@RequestBody ImageElements elements) {
        if (userContext.getUserId() == null) {
            return CompletableFuture.completedFuture(Result.unauthorized("请先登录"));
        }
        return CompletableFuture.supplyAsync(() -> {
            String imageUrl = wanxImageService.generate(elements);
            return Result.success(Map.of("imageUrl", imageUrl));
        }, imageTaskExecutor);
    }

    /**
     * SSRF 防护：校验 URL 必须使用 HTTPS 且目标地址不能是内网 IP。
     */
    private void validateRemoteUrl(String url) {
        if (url == null || !url.matches("^https://.+")) {
            throw new IllegalArgumentException("URL must use HTTPS");
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("URL must have a valid host");
            }
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Internal URLs are not allowed");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + e.getMessage());
        }
    }
}

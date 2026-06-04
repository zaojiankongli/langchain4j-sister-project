package com.zjkl.ai.image.controller;

import com.zjkl.ai.image.domain.ImageElements;
import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.ai.image.service.ImageElementExtractor;
import com.zjkl.ai.image.service.WanxImageService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 图片服务接口
 */
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
@Validated
public class ImageController {

    private final ImageDescriptionService imageDescriptionService;
    private final ImageElementExtractor imageElementExtractor;
    private final WanxImageService wanxImageService;
    private final UserContext userContext;

    @GetMapping("/describe")
    public Result<Map<String, String>> describe(@RequestParam @Pattern(regexp = "^https?://.+") String imageUrl) {
        if (userContext.getUserId() == null) {
            return Result.unauthorized("请先登录");
        }
        String description = imageDescriptionService.describe(imageUrl);
        return Result.success(Map.of("imageUrl", imageUrl, "description", description));
    }

    @GetMapping("/describe/peek")
    public Result<Map<String, String>> describeForPeek(@RequestParam @Pattern(regexp = "^https?://.+") String imageUrl) {
        if (userContext.getUserId() == null) {
            return Result.unauthorized("请先登录");
        }
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
    public Result<Map<String, String>> generateImage(@RequestBody ImageElements elements) {
        if (userContext.getUserId() == null) {
            return Result.unauthorized("请先登录");
        }
        String imageUrl = wanxImageService.generate(elements);
        return Result.success(Map.of("imageUrl", imageUrl));
    }
}

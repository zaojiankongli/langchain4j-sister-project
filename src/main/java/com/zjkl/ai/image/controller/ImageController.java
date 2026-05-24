package com.zjkl.ai.image.controller;

import com.zjkl.ai.image.service.ImageDescriptionService;
import com.zjkl.ai.image.service.ImageElementExtractor;
import com.zjkl.ai.image.domain.ImageElements;
import com.zjkl.ai.image.service.WanxImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 图片服务测试接口
 */
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageController {

    private final ImageDescriptionService imageDescriptionService;
    private final ImageElementExtractor imageElementExtractor;
    private final WanxImageService wanxImageService;

    @GetMapping("/describe")
    public Map<String, String> describe(@RequestParam String imageUrl) {
        String description = imageDescriptionService.describe(imageUrl);
        return Map.of("imageUrl", imageUrl, "description", description);
    }

    @GetMapping("/describe/peek")
    public Map<String, String> describeForPeek(@RequestParam String imageUrl) {
        String description = imageDescriptionService.describeForPeek(imageUrl);
        return Map.of("imageUrl", imageUrl, "description", description);
    }

    @PostMapping("/extract-elements")
    public ImageElements extractElements(@RequestBody Map<String, String> request) {
        String memoryContent = request.get("memoryContent");
        return imageElementExtractor.extract(memoryContent);
    }

    @PostMapping("/generate")
    public Map<String, String> generateImage(@RequestBody ImageElements elements) {
        String imageUrl = wanxImageService.generate(elements);
        return Map.of("imageUrl", imageUrl);
    }
}

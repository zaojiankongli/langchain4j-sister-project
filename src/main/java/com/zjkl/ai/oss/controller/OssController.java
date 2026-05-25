package com.zjkl.ai.oss.controller;

import com.zjkl.ai.oss.service.OssService;
import com.zjkl.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件服务测试接口
 */
@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    @PostMapping("/upload/message-image")
    public Result<Map<String, String>> uploadMessageImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) throws Exception {
        String url = ossService.uploadMessageImage(file, userId);
        return Result.success(Map.of("url", url));
    }

    @PostMapping("/upload/from-url")
    public Result<Map<String, String>> uploadFromUrl(
            @RequestParam String fileUrl,
            @RequestParam(required = false) String folder) throws Exception {
        String url = ossService.uploadFromUrl(fileUrl, folder);
        return Result.success(Map.of("url", url));
    }

    @DeleteMapping("/delete")
    public Result<Map<String, String>> deleteFile(@RequestParam String objectKey) {
        ossService.deleteFile(objectKey);
        return Result.success(Map.of("message", "删除成功", "objectKey", objectKey));
    }

    @GetMapping("/presigned-url")
    public Result<Map<String, String>> getPresignedUrl(
            @RequestParam String objectKey,
            @RequestParam(defaultValue = "60") int expirationMinutes) {
        String url = ossService.generatePresignedUrl(objectKey, expirationMinutes);
        return Result.success(Map.of("url", url));
    }
}

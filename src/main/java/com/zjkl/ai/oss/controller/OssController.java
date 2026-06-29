package com.zjkl.ai.oss.controller;

import com.zjkl.ai.oss.service.OssService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件服务接口。
 */
@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
@Validated
public class OssController {

    private final OssService ossService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @PostMapping("/upload/message-image")
    public Result<Map<String, String>> uploadMessageImage(
            @RequestParam("file") MultipartFile file) throws Exception {
        return endpointMetrics.recordCheckedResult("web", "oss.upload_message_image", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            String url = ossService.uploadMessageImage(file, userId);
            return Result.success(Map.of("url", url));
        });
    }

    @PostMapping("/upload/from-url")
    public Result<Map<String, String>> uploadFromUrl(
            @RequestParam String fileUrl,
            @RequestParam(required = false) String folder) throws Exception {
        return endpointMetrics.recordCheckedResult("web", "oss.upload_from_url", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            if (folder != null && (folder.contains("..") || folder.contains("/") || folder.contains("\\"))) {
                return Result.error(400, "文件夹名称非法");
            }
            String url = ossService.uploadFromUrl(fileUrl, folder);
            return Result.success(Map.of("url", url));
        });
    }

    @DeleteMapping("/delete")
    public Result<Map<String, String>> deleteFile(@RequestParam String objectKey) {
        return endpointMetrics.recordResult("web", "oss.delete", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            if (!isOwnedBy(objectKey, userId)) {
                return Result.error(403, "无权删除其他用户的文件");
            }
            ossService.deleteFile(objectKey);
            return Result.success(Map.of("message", "删除成功", "objectKey", objectKey));
        });
    }

    @GetMapping("/presigned-url")
    public Result<Map<String, String>> getPresignedUrl(
            @RequestParam String objectKey,
            @RequestParam(defaultValue = "60") @Min(1) @Max(480) int expirationMinutes) {
        return endpointMetrics.recordResult("web", "oss.presigned_url", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            if (!isOwnedBy(objectKey, userId)) {
                return Result.error(403, "无权访问其他用户的文件");
            }
            String url = ossService.generatePresignedUrl(objectKey, expirationMinutes);
            return Result.success(Map.of("url", url));
        });
    }

    /**
     * OSS key 格式应为 folder/userId/filename，第二段必须匹配当前用户 ID。
     */
    private boolean isOwnedBy(String objectKey, String userId) {
        if (objectKey == null || userId == null) return false;
        String[] segments = objectKey.split("/");
        return segments.length >= 3 && userId.equals(segments[1]);
    }
}

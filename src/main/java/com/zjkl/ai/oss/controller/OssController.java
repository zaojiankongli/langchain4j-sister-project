package com.zjkl.ai.oss.controller;

import com.zjkl.ai.oss.service.OssService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件服务测试接口
 */
@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
@Validated
public class OssController {

    private final OssService ossService;
    private final UserContext userContext;

    @PostMapping("/upload/message-image")
    public Result<Map<String, String>> uploadMessageImage(
            @RequestParam("file") MultipartFile file) throws Exception {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        String url = ossService.uploadMessageImage(file, userId);
        return Result.success(Map.of("url", url));
    }

    @PostMapping("/upload/from-url")
    public Result<Map<String, String>> uploadFromUrl(
            @RequestParam String fileUrl,
            @RequestParam(required = false) String folder) throws Exception {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        if (folder != null && (folder.contains("..") || folder.contains("/") || folder.contains("\\"))) {
            return Result.error(400, "文件夹名称非法");
        }
        String url = ossService.uploadFromUrl(fileUrl, folder);
        return Result.success(Map.of("url", url));
    }

    @DeleteMapping("/delete")
    public Result<Map<String, String>> deleteFile(@RequestParam String objectKey) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        // 所有权检查：objectKey 路径段必须包含当前用户 ID，防止越权删除
        if (!isOwnedBy(objectKey, userId)) {
            return Result.error(403, "无权删除其他用户的文件");
        }
        ossService.deleteFile(objectKey);
        return Result.success(Map.of("message", "删除成功", "objectKey", objectKey));
    }

    @GetMapping("/presigned-url")
    public Result<Map<String, String>> getPresignedUrl(
            @RequestParam String objectKey,
            @RequestParam(defaultValue = "60") @Min(1) @Max(480) int expirationMinutes) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        // 所有权检查：objectKey 路径段必须包含当前用户 ID
        if (!isOwnedBy(objectKey, userId)) {
            return Result.error(403, "无权访问其他用户的文件");
        }
        String url = ossService.generatePresignedUrl(objectKey, expirationMinutes);
        return Result.success(Map.of("url", url));
    }

    /**
     * 检查 objectKey 是否属于指定用户。
     * OSS key 格式为 {@code folder/userId/filename}，检查第 2 个段（index 1）是否为 userId。
     */
    private boolean isOwnedBy(String objectKey, String userId) {
        if (objectKey == null || userId == null) return false;
        for (String segment : objectKey.split("/")) {
            if (userId.equals(segment)) return true;
        }
        return false;
    }
}

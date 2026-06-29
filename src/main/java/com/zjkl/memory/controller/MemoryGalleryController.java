package com.zjkl.memory.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.exception.BusinessException;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.memory.gallery.service.ConversationMemoryGalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/gallery")
@RequiredArgsConstructor
public class MemoryGalleryController {

    private final ConversationMemoryGalleryService galleryService;
    private final UserContext userContext;
    private final EndpointMetrics endpointMetrics;

    @GetMapping("/overview")
    public Result<?> overview() {
        return endpointMetrics.recordResult("web", "gallery.overview", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            return Result.success(galleryService.getOverview(userId));
        });
    }

    @GetMapping("/{galleryKey}")
    public Result<?> detail(@PathVariable String galleryKey) {
        return endpointMetrics.recordResult("web", "gallery.detail", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            return Result.success(galleryService.getDetail(userId, galleryKey));
        });
    }

    @GetMapping("/debug/memory/{memoryId}")
    public Result<?> debugMemory(@PathVariable Long memoryId) {
        return endpointMetrics.recordResult("web", "gallery.debug_memory", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            try {
                return Result.success(galleryService.debugMemory(memoryId, userId));
            } catch (BusinessException e) {
                return Result.error(e.getCode(), e.getMessage());
            }
        });
    }

    @PostMapping("/debug/memory/{memoryId}/reclassify")
    public Result<?> reclassifyMemory(@PathVariable Long memoryId) {
        return endpointMetrics.recordResult("web", "gallery.reclassify_memory", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            try {
                return Result.success(galleryService.reclassifyMemory(memoryId, userId));
            } catch (BusinessException e) {
                return Result.error(e.getCode(), e.getMessage());
            }
        });
    }

    @PostMapping("/backfill")
    public Result<?> backfill() {
        return endpointMetrics.recordResult("web", "gallery.backfill", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            int count = galleryService.backfillForUser(userId);
            return Result.success(Map.of("processed", count));
        });
    }
}

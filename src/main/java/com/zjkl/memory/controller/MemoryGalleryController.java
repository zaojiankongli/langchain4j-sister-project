package com.zjkl.memory.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.memory.gallery.service.ConversationMemoryGalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/gallery")
@RequiredArgsConstructor
public class MemoryGalleryController {

    private final ConversationMemoryGalleryService galleryService;
    private final UserContext userContext;

    @GetMapping("/overview")
    public Result<?> overview() {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success(galleryService.getOverview(userId));
    }

    @GetMapping("/{galleryKey}")
    public Result<?> detail(@PathVariable String galleryKey) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        return Result.success(galleryService.getDetail(userId, galleryKey));
    }

    @GetMapping("/debug/memory/{memoryId}")
    public Result<?> debugMemory(@PathVariable Long memoryId) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        try {
            return Result.success(galleryService.debugMemory(memoryId, userId));
        } catch (com.zjkl.common.exception.BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @PostMapping("/debug/memory/{memoryId}/reclassify")
    public Result<?> reclassifyMemory(@PathVariable Long memoryId) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        try {
            return Result.success(galleryService.reclassifyMemory(memoryId, userId));
        } catch (com.zjkl.common.exception.BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @PostMapping("/backfill")
    public Result<?> backfill() {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        int count = galleryService.backfillForUser(userId);
        return Result.success(Map.of("processed", count));
    }
}

package com.zjkl.memory.gallery.domain.vo;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record GalleryUnlockVO(
        String galleryKey,
        LocalDateTime unlockedAt,
        String relatedMood,
        String relatedExcerpt,
        Long sourceMemoryId,
        String sourceMemoryTitle,
        String sourceMemoryDate,
        String sourceImageUrl
) {}

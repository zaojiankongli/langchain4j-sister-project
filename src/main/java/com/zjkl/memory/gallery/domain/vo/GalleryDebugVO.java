package com.zjkl.memory.gallery.domain.vo;

import com.zjkl.memory.gallery.domain.GalleryClassificationResult;
import com.zjkl.memory.gallery.entity.ConversationMemoryGalleryLink;
import lombok.Builder;

import java.util.List;

@Builder
public record GalleryDebugVO(
        Long memoryId,
        String userId,
        String title,
        String mood,
        String memoryDate,
        String imageUrl,
        String content,
        List<ConversationMemoryGalleryLink> persistedLinks,
        GalleryClassificationResult predictedResult
) {}

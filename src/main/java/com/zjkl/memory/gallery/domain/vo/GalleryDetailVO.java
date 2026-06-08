package com.zjkl.memory.gallery.domain.vo;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record GalleryDetailVO(
        GalleryDefinitionVO definition,
        GalleryUnlockVO unlock,
        boolean unlocked,
        BigDecimal primaryConfidence,
        List<String> matchedKeywords,
        String sourceMemoryContent
) {}

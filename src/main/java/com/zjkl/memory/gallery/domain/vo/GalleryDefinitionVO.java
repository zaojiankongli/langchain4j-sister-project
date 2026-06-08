package com.zjkl.memory.gallery.domain.vo;

import lombok.Builder;

import java.util.List;

@Builder
public record GalleryDefinitionVO(
        String galleryKey,
        String title,
        String category,
        String rarity,
        String hint,
        String description,
        String coverTheme,
        List<String> matchKeywords,
        Integer sortOrder
) {}

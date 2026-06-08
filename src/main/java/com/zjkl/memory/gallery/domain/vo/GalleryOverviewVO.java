package com.zjkl.memory.gallery.domain.vo;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record GalleryOverviewVO(
        List<GalleryDefinitionVO> definitions,
        List<GalleryUnlockVO> unlocks,
        Map<String, Integer> counts
) {}

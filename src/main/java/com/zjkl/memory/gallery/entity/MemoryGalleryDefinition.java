package com.zjkl.memory.gallery.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemoryGalleryDefinition {
    private Long id;
    private String galleryKey;
    private String title;
    private String category;
    private String rarity;
    private String hint;
    private String description;
    private String coverTheme;
    private String matchKeywords;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

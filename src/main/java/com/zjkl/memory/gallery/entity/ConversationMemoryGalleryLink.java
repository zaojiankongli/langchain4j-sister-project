package com.zjkl.memory.gallery.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ConversationMemoryGalleryLink {
    private Long id;
    private Long memoryId;
    private String galleryKey;
    private BigDecimal confidence;
    private Boolean primaryLink;
    private String matchedKeywords;
    private LocalDateTime createdAt;
}

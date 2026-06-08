package com.zjkl.memory.gallery.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserMemoryGalleryUnlock {
    private Long id;
    private String userId;
    private String galleryKey;
    private Long sourceMemoryId;
    private String relatedMood;
    private String relatedExcerpt;
    private LocalDateTime unlockedAt;
    private LocalDateTime createdAt;
}

package com.zjkl.memory.gallery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryMatchResult {
    private String galleryKey;
    private double confidence;
    private boolean primary;
    private String reason;
    private List<String> matchedKeywords;
}

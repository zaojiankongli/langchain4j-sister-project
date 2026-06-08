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
public class GalleryClassificationResult {
    private String primaryGalleryKey;
    private List<GalleryMatchResult> matches;
}

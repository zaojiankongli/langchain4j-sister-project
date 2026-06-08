package com.zjkl.memory.gallery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.memory.gallery.entity.MemoryGalleryDefinition;
import com.zjkl.memory.gallery.mapper.MemoryGalleryDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryGalleryDefinitionService {

    private final MemoryGalleryDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;

    public List<MemoryGalleryDefinition> getEnabledDefinitions() {
        return definitionMapper.selectEnabledDefinitions();
    }

    public Map<String, MemoryGalleryDefinition> getEnabledDefinitionMap() {
        return getEnabledDefinitions().stream().collect(Collectors.toMap(MemoryGalleryDefinition::getGalleryKey, Function.identity()));
    }

    public List<String> parseKeywords(MemoryGalleryDefinition definition) {
        if (definition == null || definition.getMatchKeywords() == null || definition.getMatchKeywords().isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(definition.getMatchKeywords(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析图鉴关键词失败: galleryKey={}", definition.getGalleryKey(), e);
            return Collections.emptyList();
        }
    }
}

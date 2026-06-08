package com.zjkl.memory.gallery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.memory.gallery.domain.GalleryClassificationResult;
import com.zjkl.memory.gallery.domain.GalleryMatchResult;
import com.zjkl.memory.gallery.domain.vo.GalleryDebugVO;
import com.zjkl.memory.gallery.domain.vo.GalleryDefinitionVO;
import com.zjkl.memory.gallery.domain.vo.GalleryDetailVO;
import com.zjkl.memory.gallery.domain.vo.GalleryOverviewVO;
import com.zjkl.memory.gallery.domain.vo.GalleryUnlockVO;
import com.zjkl.memory.gallery.entity.ConversationMemoryGalleryLink;
import com.zjkl.memory.gallery.entity.MemoryGalleryDefinition;
import com.zjkl.memory.gallery.entity.UserMemoryGalleryUnlock;
import com.zjkl.memory.gallery.mapper.ConversationMemoryGalleryLinkMapper;
import com.zjkl.memory.gallery.mapper.UserMemoryGalleryUnlockMapper;
import com.zjkl.memory.mapper.ConversationMemoryMapper;
import com.zjkl.user.domain.ConversationMemory;
import com.zjkl.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationMemoryGalleryService {

    private final ConversationMemoryGalleryLinkMapper linkMapper;
    private final UserMemoryGalleryUnlockMapper unlockMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final MemoryGalleryDefinitionService definitionService;
    private final MemoryGalleryClassifier classifier;
    private final ObjectMapper objectMapper;

    @Async("asyncTaskExecutor")
    public void classifyAndPersistAsync(ConversationMemory memory) {
        classifyAndPersist(memory);
    }

    @Transactional
    public void classifyAndPersist(ConversationMemory memory) {
        if (memory == null || memory.getId() == null) {
            return;
        }

        GalleryClassificationResult result = classifier.classify(memory);
        linkMapper.deleteByMemoryId(memory.getId());
        if (result == null || result.getMatches() == null || result.getMatches().isEmpty()) {
            return;
        }

        for (GalleryMatchResult match : result.getMatches()) {
            ConversationMemoryGalleryLink link = new ConversationMemoryGalleryLink();
            link.setMemoryId(memory.getId());
            link.setGalleryKey(match.getGalleryKey());
            link.setConfidence(BigDecimal.valueOf(match.getConfidence()));
            link.setPrimaryLink(match.isPrimary());
            link.setMatchedKeywords(writeJson(match.getMatchedKeywords()));
            linkMapper.insert(link);

            if (match.isPrimary()) {
                ensureUnlocked(memory, match);
            }
        }
    }

    @Transactional
    public int backfillForUser(String userId) {
        List<ConversationMemory> memories = conversationMemoryMapper.selectAllByUserId(userId);
        for (ConversationMemory memory : memories) {
            classifyAndPersist(memory);
        }
        return memories.size();
    }

    @Transactional(readOnly = true)
    public GalleryDebugVO debugMemory(Long memoryId, String currentUserId) {
        ConversationMemory memory = requireOwnedMemory(memoryId, currentUserId);
        return GalleryDebugVO.builder()
                .memoryId(memory.getId())
                .userId(memory.getUserId())
                .title(memory.getTitle())
                .mood(memory.getMood())
                .memoryDate(memory.getMemoryDate() != null ? memory.getMemoryDate().toString() : null)
                .imageUrl(memory.getImageUrl())
                .content(memory.getContent())
                .persistedLinks(linkMapper.selectByMemoryId(memoryId))
                .predictedResult(classifier.classify(memory))
                .build();
    }

    @Transactional
    public GalleryDebugVO reclassifyMemory(Long memoryId, String currentUserId) {
        ConversationMemory memory = requireOwnedMemory(memoryId, currentUserId);
        classifyAndPersist(memory);
        return debugMemory(memoryId, currentUserId);
    }

    public GalleryOverviewVO getOverview(String userId) {
        List<GalleryDefinitionVO> definitions = definitionService.getEnabledDefinitions().stream()
                .map(this::toDefinitionVO)
                .toList();
        List<GalleryUnlockVO> unlocks = unlockMapper.selectUnlockViewsByUserId(userId);
        return GalleryOverviewVO.builder()
                .definitions(definitions)
                .unlocks(unlocks)
                .counts(Map.of(
                        "total", definitions.size(),
                        "unlocked", unlocks.size()
                ))
                .build();
    }

    public GalleryDetailVO getDetail(String userId, String galleryKey) {
        MemoryGalleryDefinition definition = definitionService.getEnabledDefinitionMap().get(galleryKey);
        GalleryUnlockVO unlock = unlockMapper.selectUnlockViewByUserIdAndGalleryKey(userId, galleryKey);
        ConversationMemoryGalleryLink primaryLink = null;
        ConversationMemory sourceMemory = null;

        if (unlock != null && unlock.sourceMemoryId() != null) {
            sourceMemory = conversationMemoryMapper.selectById(unlock.sourceMemoryId());
            primaryLink = linkMapper.selectPrimaryByMemoryIdAndGalleryKey(unlock.sourceMemoryId(), galleryKey);
        }

        return GalleryDetailVO.builder()
                .definition(definition == null ? null : toDefinitionVO(definition))
                .unlock(unlock)
                .unlocked(unlock != null)
                .primaryConfidence(primaryLink != null ? primaryLink.getConfidence() : null)
                .matchedKeywords(parseJsonArray(primaryLink != null ? primaryLink.getMatchedKeywords() : null))
                .sourceMemoryContent(sourceMemory != null ? sourceMemory.getContent() : null)
                .build();
    }

    private void ensureUnlocked(ConversationMemory memory, GalleryMatchResult match) {
        UserMemoryGalleryUnlock existing = unlockMapper.selectByUserIdAndGalleryKey(memory.getUserId(), match.getGalleryKey());
        if (existing != null) {
            return;
        }

        UserMemoryGalleryUnlock unlock = new UserMemoryGalleryUnlock();
        unlock.setUserId(memory.getUserId());
        unlock.setGalleryKey(match.getGalleryKey());
        unlock.setSourceMemoryId(memory.getId());
        unlock.setRelatedMood(memory.getMood());
        unlock.setRelatedExcerpt(excerpt(memory.getContent()));
        unlock.setUnlockedAt(LocalDateTime.now());
        unlockMapper.insert(unlock);
    }

    private ConversationMemory requireOwnedMemory(Long memoryId, String currentUserId) {
        ConversationMemory memory = conversationMemoryMapper.selectById(memoryId);
        if (memory == null) {
            throw new BusinessException(404, "记忆不存在");
        }
        if (currentUserId == null || !currentUserId.equals(memory.getUserId())) {
            throw new BusinessException(403, "无权访问该记忆");
        }
        return memory;
    }

    private GalleryDefinitionVO toDefinitionVO(MemoryGalleryDefinition definition) {
        return GalleryDefinitionVO.builder()
                .galleryKey(definition.getGalleryKey())
                .title(definition.getTitle())
                .category(definition.getCategory())
                .rarity(definition.getRarity())
                .hint(definition.getHint())
                .description(definition.getDescription())
                .coverTheme(definition.getCoverTheme())
                .matchKeywords(definitionService.parseKeywords(definition))
                .sortOrder(definition.getSortOrder())
                .build();
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.length() <= 120 ? content : content.substring(0, 120);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("图鉴 JSON 序列化失败", e);
            return "[]";
        }
    }

    private List<String> parseJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(value);
        } catch (Exception e) {
            log.warn("图鉴 JSON 反序列化失败", e);
            return Collections.emptyList();
        }
    }
}

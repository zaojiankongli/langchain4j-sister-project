package com.zjkl.memory.gallery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.memory.gallery.domain.GalleryClassificationResult;
import com.zjkl.memory.gallery.domain.GalleryMatchResult;
import com.zjkl.memory.gallery.entity.MemoryGalleryDefinition;
import com.zjkl.user.domain.ConversationMemory;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryGalleryClassifier {

    private static final String TEMPLATE_KEY = "gallery-classifier";

    private final QwenChatModel qwenChatModel;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final MemoryGalleryDefinitionService definitionService;

    public GalleryClassificationResult classify(ConversationMemory memory) {
        List<MemoryGalleryDefinition> definitions = definitionService.getEnabledDefinitions();
        if (definitions.isEmpty()) {
            return GalleryClassificationResult.builder().primaryGalleryKey(null).matches(List.of()).build();
        }

        List<RuleCandidate> candidates = recallCandidates(memory, definitions);
        if (candidates.isEmpty()) {
            return GalleryClassificationResult.builder().primaryGalleryKey(null).matches(List.of()).build();
        }

        GalleryClassificationResult aiResult = adjudicateWithAi(memory, candidates);
        if (aiResult != null && aiResult.getMatches() != null && !aiResult.getMatches().isEmpty()) {
            return mergeMatchedKeywords(aiResult, candidates);
        }

        return fallbackFromRules(candidates);
    }

    private List<RuleCandidate> recallCandidates(ConversationMemory memory, List<MemoryGalleryDefinition> definitions) {
        String title = normalize(memory.getTitle());
        String content = normalize(memory.getContent());
        String text = title + " " + content;
        String mood = normalize(memory.getMood());

        List<RuleCandidate> candidates = new ArrayList<>();
        for (MemoryGalleryDefinition definition : definitions) {
            double score = 0D;
            List<String> matchedKeywords = new ArrayList<>();
            for (String keyword : definitionService.parseKeywords(definition)) {
                String normalizedKeyword = normalize(keyword);
                if (!normalizedKeyword.isBlank() && text.contains(normalizedKeyword)) {
                    matchedKeywords.add(keyword);
                    score += 1.2D;
                }
            }

            String category = normalize(definition.getCategory());
            if ("emotion".equals(category) && !mood.isBlank()) {
                if (text.contains(mood)) {
                    score += 1.5D;
                }
                if (containsAny(text, "开心", "平静", "伤心", "难过", "想念", "温柔")) {
                    score += 0.8D;
                }
            }
            if ("story".equals(category) && containsAny(text, "一起", "故事", "回信", "声音", "听见")) {
                score += 0.8D;
            }
            if ("cg".equals(category) && memory.getImageUrl() != null && !memory.getImageUrl().isBlank()) {
                score += 1.0D;
            }
            if ("daily".equals(category) && containsAny(text, "第一次", "聊天", "开口", "开始")) {
                score += 0.7D;
            }

            if (score > 0D) {
                candidates.add(new RuleCandidate(definition, score, matchedKeywords));
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(RuleCandidate::score).reversed())
                .limit(5)
                .toList();
    }

    private GalleryClassificationResult adjudicateWithAi(ConversationMemory memory, List<RuleCandidate> candidates) {
        try {
            Set<String> allowedKeys = candidates.stream()
                    .map(candidate -> candidate.definition().getGalleryKey())
                    .collect(Collectors.toCollection(HashSet::new));

            List<Map<String, Object>> candidatePayload = candidates.stream().map(candidate -> Map.of(
                    "galleryKey", candidate.definition().getGalleryKey(),
                    "title", candidate.definition().getTitle(),
                    "category", candidate.definition().getCategory(),
                    "rarity", candidate.definition().getRarity(),
                    "hint", Objects.toString(candidate.definition().getHint(), ""),
                    "description", Objects.toString(candidate.definition().getDescription(), ""),
                    "matchKeywords", definitionService.parseKeywords(candidate.definition()),
                    "ruleScore", candidate.score()
            )).toList();

            String prompt = promptTemplateService.render(TEMPLATE_KEY, Map.of(
                    "memoryTitle", Objects.toString(memory.getTitle(), ""),
                    "memoryContent", Objects.toString(memory.getContent(), ""),
                    "memoryMood", Objects.toString(memory.getMood(), ""),
                    "memoryDate", memory.getMemoryDate() != null ? memory.getMemoryDate().toString() : "",
                    "imageUrl", Objects.toString(memory.getImageUrl(), ""),
                    "candidatesJson", objectMapper.writeValueAsString(candidatePayload)
            ));

            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from("你是一个回忆图鉴分类器，只输出 JSON。"),
                            UserMessage.from(prompt)
                    )
                    .build());

            String text = response.aiMessage() != null ? response.aiMessage().text() : null;
            if (text == null || text.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(extractJson(text));
            String primary = root.path("primaryGalleryKey").asText(null);
            if (primary != null && !allowedKeys.contains(primary)) {
                log.warn("图鉴 AI 返回了非法 primaryGalleryKey，回退规则结果: memoryId={}, galleryKey={}", memory.getId(), primary);
                return null;
            }
            JsonNode matchesNode = root.path("matches");
            List<GalleryMatchResult> matches = new ArrayList<>();
            if (matchesNode.isArray()) {
                for (JsonNode node : matchesNode) {
                    String galleryKey = node.path("galleryKey").asText(null);
                    if (galleryKey == null || galleryKey.isBlank() || !allowedKeys.contains(galleryKey)) {
                        continue;
                    }
                    matches.add(GalleryMatchResult.builder()
                            .galleryKey(galleryKey)
                            .confidence(node.path("confidence").asDouble(0.0D))
                            .primary(galleryKey.equals(primary))
                            .reason(node.path("reason").asText(""))
                            .matchedKeywords(List.of())
                            .build());
                }
            }
            if (matches.isEmpty()) {
                return null;
            }

            final String initialPrimary = primary;
            if (initialPrimary == null || initialPrimary.isBlank() || matches.stream().noneMatch(match -> galleryKeyEquals(match, initialPrimary))) {
                GalleryMatchResult highestConfidence = matches.stream()
                        .max(Comparator.comparingDouble(GalleryMatchResult::getConfidence))
                        .orElse(null);
                if (highestConfidence == null) {
                    return null;
                }
                primary = highestConfidence.getGalleryKey();
            }

            final String resolvedPrimary = primary;
            matches.forEach(match -> match.setPrimary(galleryKeyEquals(match, resolvedPrimary)));
            return GalleryClassificationResult.builder()
                    .primaryGalleryKey(primary)
                    .matches(matches)
                    .build();
        } catch (Exception e) {
            log.warn("图鉴 AI 裁决失败，将回退规则排序: memoryId={}", memory.getId(), e);
            return null;
        }
    }

    private GalleryClassificationResult fallbackFromRules(List<RuleCandidate> candidates) {
        double maxScore = candidates.stream().mapToDouble(RuleCandidate::score).max().orElse(1D);
        List<GalleryMatchResult> matches = candidates.stream().map(candidate -> GalleryMatchResult.builder()
                        .galleryKey(candidate.definition().getGalleryKey())
                        .confidence(Math.min(0.99D, candidate.score() / Math.max(1D, maxScore)))
                        .primary(false)
                        .reason("规则召回匹配")
                        .matchedKeywords(candidate.matchedKeywords())
                        .build())
                .toList();
        String primary = matches.get(0).getGalleryKey();
        matches.get(0).setPrimary(true);
        return GalleryClassificationResult.builder().primaryGalleryKey(primary).matches(matches).build();
    }

    private GalleryClassificationResult mergeMatchedKeywords(GalleryClassificationResult result, List<RuleCandidate> candidates) {
        Map<String, List<String>> keywordMap = candidates.stream().collect(Collectors.toMap(
                candidate -> candidate.definition().getGalleryKey(),
                RuleCandidate::matchedKeywords,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        List<GalleryMatchResult> merged = result.getMatches().stream().peek(match ->
                match.setMatchedKeywords(keywordMap.getOrDefault(match.getGalleryKey(), List.of()))
        ).toList();
        result.setMatches(merged);
        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private boolean galleryKeyEquals(GalleryMatchResult match, String galleryKey) {
        return Objects.equals(match.getGalleryKey(), galleryKey);
    }

    private record RuleCandidate(MemoryGalleryDefinition definition, double score, List<String> matchedKeywords) {}
}

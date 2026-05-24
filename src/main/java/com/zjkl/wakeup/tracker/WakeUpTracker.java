package com.zjkl.wakeup.tracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class WakeUpTracker {

    private final StringRedisTemplate redisTemplate;

    private static final String RECORD_KEY_PREFIX = "wakeup:record:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double AB_TEST_RATIO = 0.05;
    private static final long REPLY_WINDOW_MINUTES = 30;

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("WakeUpTracker 初始化完成, AB_TEST_RATIO={}", AB_TEST_RATIO);
    }

    public SwapResult maybeSwap(List<String> candidates, int[] scores, int bestIndex) {
        List<Integer> validIndices = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i) != null) validIndices.add(i);
        }
        if (validIndices.isEmpty()) {
            return new SwapResult(bestIndex, null, false);
        }
        int actualBestIdx = validIndices.contains(bestIndex) ? bestIndex : validIndices.get(0);
        if (validIndices.size() < 2) {
            return new SwapResult(actualBestIdx, candidates.get(actualBestIdx), false);
        }
        if (ThreadLocalRandom.current().nextDouble() >= AB_TEST_RATIO) {
            return new SwapResult(actualBestIdx, candidates.get(actualBestIdx), false);
        }
        int swapIndex = findSwapCandidate(validIndices, actualBestIdx);
        log.info("A/B 测试采样：最佳索引={}, 实际发送索引={}", actualBestIdx, swapIndex);
        return new SwapResult(actualBestIdx, candidates.get(swapIndex), true);
    }

    private int findSwapCandidate(List<Integer> validIndices, int bestIndex) {
        for (int i = 0; i < 5; i++) {
            int idx = validIndices.get(ThreadLocalRandom.current().nextInt(validIndices.size()));
            if (idx != bestIndex) return idx;
        }
        int bestPos = validIndices.indexOf(bestIndex);
        return validIndices.get((bestPos + 1) % validIndices.size());
    }

    public void recordSent(String userId, List<String> candidates, int[] scores,
                           int bestIndex, int actualIndex, String finalMessage) {
        try {
            String key = RECORD_KEY_PREFIX + userId + ":" + LocalDate.now().format(DATE_FMT);
            WakeUpRecord record = new WakeUpRecord();
            record.setTimestamp(System.currentTimeMillis());
            record.setCandidates(candidates);
            record.setScores(scores);
            record.setBestIndex(bestIndex);
            record.setActualIndex(actualIndex);
            record.setFinalMessage(finalMessage);
            record.setUserReplied(false);

            String existing = redisTemplate.opsForValue().get(key);
            List<WakeUpRecord> records;
            if (existing != null) {
                try {
                    records = objectMapper.readValue(existing,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, WakeUpRecord.class));
                } catch (Exception e) {
                    records = new ArrayList<>();
                }
            } else {
                records = new ArrayList<>();
            }
            records.add(record);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(records));
        } catch (Exception e) {
            log.warn("记录唤醒发送失败: userId={}", userId, e);
        }
    }

    public void markUserReplied(String userId) {
        try {
            String key = RECORD_KEY_PREFIX + userId + ":" + LocalDate.now().format(DATE_FMT);
            String existing = redisTemplate.opsForValue().get(key);
            if (existing == null) return;

            List<WakeUpRecord> records = objectMapper.readValue(existing,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, WakeUpRecord.class));

            long now = System.currentTimeMillis();
            boolean updated = false;
            for (int i = records.size() - 1; i >= 0; i--) {
                WakeUpRecord record = records.get(i);
                if (!record.isUserReplied() &&
                        (now - record.getTimestamp()) < REPLY_WINDOW_MINUTES * 60 * 1000) {
                    record.setUserReplied(true);
                    updated = true;
                    break;
                }
            }
            if (updated) {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(records));
                log.debug("标记用户已回复唤醒消息: userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("标记用户回复失败: userId={}", userId, e);
        }
    }

    @Data
    public static class WakeUpRecord {
        private long timestamp;
        private List<String> candidates;
        private int[] scores;
        private int bestIndex;
        private int actualIndex;
        private String finalMessage;
        private boolean userReplied;
    }

    @Data
    public static class SwapResult {
        private final int originalBestIndex;
        private final String message;
        private final boolean isSwapped;

        public SwapResult(int originalBestIndex, String message, boolean isSwapped) {
            this.originalBestIndex = originalBestIndex;
            this.message = message;
            this.isSwapped = isSwapped;
        }
    }
}

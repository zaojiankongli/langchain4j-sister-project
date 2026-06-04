package com.zjkl.wakeup.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.wakeup.tool.UserStateTool;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class WakeUpTracker {

    private final StringRedisTemplate redisTemplate;
    private final UserStateTool userStateTool;
    private final ObjectMapper objectMapper;

    private static final String RECORD_KEY_PREFIX = "wakeup:record:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double AB_TEST_RATIO = 0.05;
    private static final long REPLY_WINDOW_MINUTES = 30;

    /**
     * Lua script for atomic append to a JSON array stored in Redis.
     * KEYS[1] = record key, ARGV[1] = new record JSON, ARGV[2] = TTL seconds.
     * If the key does not exist, initializes a new array.
     */
    private static final String ATOMIC_APPEND_LUA =
            "local key = KEYS[1]\n" +
            "local newRecord = ARGV[1]\n" +
            "local ttl = tonumber(ARGV[2])\n" +
            "local existing = redis.call('GET', key)\n" +
            "local arr\n" +
            "if existing and #existing > 0 then\n" +
            "  arr = existing:sub(1, -2) .. ',' .. newRecord .. ']'\n" +
            "else\n" +
            "  arr = '[' .. newRecord .. ']'\n" +
            "end\n" +
            "redis.call('SET', key, arr, 'EX', ttl)\n" +
            "return 1";
    private static final DefaultRedisScript<Long> ATOMIC_APPEND_SCRIPT = new DefaultRedisScript<>(ATOMIC_APPEND_LUA, Long.class);

    /**
     * Lua script for atomic markUserReplied.
     * Scans forward through the JSON array, tracking each record's start position.
     * For every "userReplied":false occurrence, checks the record's timestamp against
     * the reply window and flips the flag if within range — all in a single atomic EVAL.
     * KEYS[1] = record key, ARGV[1] = reply window millis, ARGV[2] = current time millis, ARGV[3] = TTL days.
     */
    private static final String MARK_REPLIED_LUA =
            "local key = KEYS[1]\n" +
            "local windowMs = tonumber(ARGV[1])\n" +
            "local now = tonumber(ARGV[2])\n" +
            "local ttlDays = tonumber(ARGV[3])\n" +
            "local json = redis.call('GET', key)\n" +
            "if not json then return 0 end\n" +
            "local searchStr = '\"userReplied\":false'\n" +
            "local tsPrefix = '\"timestamp\":'\n" +
            "local searchLen = #searchStr\n" +
            "local jsonLen = #json\n" +
            "local searchPos = 1\n" +
            "local recordStart = nil\n" +
            "while searchPos <= jsonLen do\n" +
            "  recordStart = string.find(json, '{', searchPos, true)\n" +
            "  if not recordStart then break end\n" +
            "  local nextRecStart = string.find(json, '{', recordStart + 1, true)\n" +
            "  local recEnd = (nextRecStart and nextRecStart - 1) or jsonLen\n" +
            "  local found = string.find(json, searchStr, recordStart, true)\n" +
            "  if found and found <= recEnd then\n" +
            "    local tsPos = string.find(json, tsPrefix, recordStart, true)\n" +
            "    if tsPos and tsPos < found then\n" +
            "      local tsEnd = string.find(json, ',', tsPos + #tsPrefix) or string.find(json, '}', tsPos + #tsPrefix)\n" +
            "      if tsEnd then\n" +
            "        local ts = tonumber(string.sub(json, tsPos + #tsPrefix, tsEnd - 1))\n" +
            "        if ts and (now - ts) < windowMs then\n" +
            "          local updated = string.sub(json, 1, found - 1) .. '\"userReplied\":true' .. string.sub(json, found + searchLen)\n" +
            "          redis.call('SET', key, updated, 'EX', ttlDays * 86400)\n" +
            "          return 1\n" +
            "        end\n" +
            "      end\n" +
            "    end\n" +
            "  end\n" +
            "  searchPos = recEnd + 1\n" +
            "end\n" +
            "return 0";
    private static final DefaultRedisScript<Long> MARK_REPLIED_SCRIPT = new DefaultRedisScript<>(MARK_REPLIED_LUA, Long.class);

    private static final long RECORD_TTL_SECONDS = Duration.ofDays(7).toSeconds();

    public SwapResult maybeSwap(List<String> candidates, int[] scores, int bestIndex) {
        return maybeSwap(candidates, scores, bestIndex, ThreadLocalRandom.current().nextDouble(), null);
    }

    SwapResult maybeSwap(List<String> candidates, int[] scores, int bestIndex,
                         double randomValue, Integer forcedSwapIndex) {
        List<Integer> validIndices = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i) != null) validIndices.add(i);
        }
        if (validIndices.isEmpty()) {
            return new SwapResult(bestIndex, -1, null, false);
        }
        int actualBestIdx = validIndices.contains(bestIndex) ? bestIndex : validIndices.get(0);
        if (validIndices.size() < 2) {
            return new SwapResult(actualBestIdx, actualBestIdx, candidates.get(actualBestIdx), false);
        }
        if (randomValue >= AB_TEST_RATIO) {
            return new SwapResult(actualBestIdx, actualBestIdx, candidates.get(actualBestIdx), false);
        }
        int swapIndex = forcedSwapIndex != null ? forcedSwapIndex : findSwapCandidate(validIndices, actualBestIdx);
        log.info("A/B 测试采样：最佳索引={}, 实际发送索引={}", actualBestIdx, swapIndex);
        return new SwapResult(actualBestIdx, swapIndex, candidates.get(swapIndex), true);
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

            String recordJson = objectMapper.writeValueAsString(record);
            redisTemplate.execute(ATOMIC_APPEND_SCRIPT,
                    Collections.singletonList(key),
                    recordJson,
                    String.valueOf(RECORD_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("记录唤醒发送失败: userId={}", userId, e);
        }
    }

    public void markUserReplied(String userId) {
        try {
            String key = RECORD_KEY_PREFIX + userId + ":" + LocalDate.now().format(DATE_FMT);
            long windowMs = REPLY_WINDOW_MINUTES * 60 * 1000;
            long nowMs = System.currentTimeMillis();

            Long result = redisTemplate.execute(MARK_REPLIED_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(windowMs),
                    String.valueOf(nowMs),
                    "7");
            if (result != null && result == 1L) {
                log.debug("标记用户已回复唤醒消息: userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("标记用户回复失败: userId={}", userId, e);
        }
    }

    /**
     * 获取距上次唤醒的分钟数（委托给 UserStateTool，避免重复实现）
     */
    public Integer getMinutesSinceLastWakeup(String userId) {
        return userStateTool.getMinutesSinceLastWakeup(userId);
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
        private final int actualSentIndex;
        private final String message;
        private final boolean isSwapped;

        public SwapResult(int originalBestIndex, int actualSentIndex, String message, boolean isSwapped) {
            this.originalBestIndex = originalBestIndex;
            this.actualSentIndex = actualSentIndex;
            this.message = message;
            this.isSwapped = isSwapped;
        }
    }
}

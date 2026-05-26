package com.zjkl.wakeup.scorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.wakeup.agent.WakeUpScoreResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评分结果解析器 — 负责解析 Scorer Agent 的 JSON 输出为结构化分数。
 */
@Slf4j
@Component
public class WakeUpScorer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析 Scorer Agent 的评分 JSON 结果
     *
     * @param json Scorer 返回的 JSON 字符串
     * @return 解析后的打分结果，解析失败默认返回 5 分
     */
    public WakeUpScoreResult parseScoreResult(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            int score = node.path("score").asInt(5);
            String reason = node.path("reason").asText("无理由");
            return new WakeUpScoreResult(score, reason);
        } catch (Exception e) {
            log.warn("解析评分 JSON 失败: {}", json, e);
            return new WakeUpScoreResult(5, "解析失败");
        }
    }
}

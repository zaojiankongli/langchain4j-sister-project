package com.zjkl.wakeup.arbiter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.wakeup.tool.TimeContextTool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仲裁决策解析器 — 负责解析 Arbiter Agent 的 JSON 输出、选取最终候选消息。
 */
@Slf4j
@Component
public class WakeUpArbiter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析 Arbiter Agent 的仲裁 JSON 结果
     *
     * @param json       Arbiter 返回的 JSON
     * @param candidates 候选消息列表（String，含 null）
     * @param timeContext 时间上下文（用于构建 fallback）
     * @return 仲裁决策结果
     */
    public ArbiterDecision parseArbiterResult(String json, List<String> candidates,
                                               TimeContextTool.TimeContext timeContext) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String decision = node.path("decision").asText("direct");
            int selectedIndex = node.path("selectedIndex").asInt(0);
            String mergedMessage = node.path("mergedMessage").asText(null);

            switch (decision) {
                case "merge":
                    if (mergedMessage != null && !mergedMessage.isBlank()) {
                        return new ArbiterDecision(0, mergedMessage);
                    }
                    return pickCandidate(candidates, selectedIndex, timeContext);
                case "fallback":
                    String fallback = timeContext.greeting() + "～今天过得怎么样呀";
                    return new ArbiterDecision(0, fallback);
                default:
                    return pickCandidate(candidates, selectedIndex, timeContext);
            }
        } catch (Exception e) {
            log.warn("解析仲裁 JSON 失败，使用第一条候选: {}", json, e);
            String firstValid = null;
            for (String c : candidates) {
                if (c != null) {
                    firstValid = c;
                    break;
                }
            }
            return new ArbiterDecision(0, firstValid != null ? firstValid : timeContext.greeting() + "～今天过得怎么样呀");
        }
    }

    /**
     * 从候选列表中选取消息
     *
     * @param candidates     候选消息列表（含 null）
     * @param preferredIndex 首选索引
     * @param timeContext    时间上下文（用于构建 fallback）
     * @return 选中的决策结果
     */
    public ArbiterDecision pickCandidate(List<String> candidates, int preferredIndex,
                                          TimeContextTool.TimeContext timeContext) {
        int idx = Math.min(preferredIndex, candidates.size() - 1);
        String msg = candidates.get(idx);
        if (msg != null) {
            return new ArbiterDecision(idx, msg);
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i) != null) {
                return new ArbiterDecision(i, candidates.get(i));
            }
        }
        return new ArbiterDecision(0, timeContext.greeting() + "～今天过得怎么样呀");
    }

    /**
     * 仲裁决策结果
     */
    @Data
    @AllArgsConstructor
    public static class ArbiterDecision {
        private int bestIndex;
        private String message;
    }
}

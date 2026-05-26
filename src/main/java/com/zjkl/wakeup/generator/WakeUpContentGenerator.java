package com.zjkl.wakeup.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.emotion.model.VoiceSynthesisParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 生成内容处理器 — 负责解析 Generator Agent 的输出、校验候选消息、选取最终输出。
 */
@Slf4j
@Component
public class WakeUpContentGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析 Generator Agent 的 JSON 输出
     *
     * @param raw 原始输出字符串
     * @return 解析后的 GeneratorOutput，解析失败返回 null
     */
    public GeneratorOutput parseGeneratorOutput(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            String msg = trimmed.replaceAll("^[\"']+|[\"']+$", "").trim();
            if (msg.length() < 4) return null;
            msg = truncateMessage(msg);
            return new GeneratorOutput(msg, null);
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            String message = node.path("message").asText(null);
            if (message == null || message.isBlank()) return null;
            message = message.trim().replaceAll("^[\"']+|[\"']+$", "");
            message = truncateMessage(message);
            VoiceSynthesisParam vp = parseVoiceParams(node.path("voiceParams"));
            return new GeneratorOutput(message, vp);
        } catch (Exception e) {
            log.warn("解析 Generator JSON 失败: {}", raw, e);
            return null;
        }
    }

    /**
     * 截断消息到 20 字
     */
    public String truncateMessage(String msg) {
        if (msg != null && msg.length() > 20) {
            return msg.substring(0, 20);
        }
        return msg;
    }

    /**
     * 解析语音合成参数
     *
     * @param vpNode JSON 中的 voiceParams 节点
     * @return 语音参数对象，无参数时返回 null
     */
    public VoiceSynthesisParam parseVoiceParams(JsonNode vpNode) {
        if (vpNode == null || vpNode.isNull() || vpNode.isMissingNode()) return null;
        VoiceSynthesisParam vp = new VoiceSynthesisParam();
        if (vpNode.has("volume")) vp.setVolume(vpNode.path("volume").asInt(50));
        if (vpNode.has("speechRate")) vp.setSpeechRate((float) vpNode.path("speechRate").asDouble(1.0));
        if (vpNode.has("pitchRate")) vp.setPitchRate((float) vpNode.path("pitchRate").asDouble(1.0));
        if (vpNode.has("instruction")) vp.setInstruction(vpNode.path("instruction").asText(null));
        return vp;
    }

    /**
     * 判断候选消息是否有效（不为 null / blank，不含无效关键词）
     *
     * @param output Generator 输出
     * @return true 有效
     */
    public boolean isValidCandidate(GeneratorOutput output) {
        if (output == null || output.getMessage() == null || output.getMessage().isBlank()) return false;
        String msg = output.getMessage();
        return !msg.contains("无可用") && !msg.contains("无相关");
    }

    /**
     * 根据仲裁索引选取最终的 GeneratorOutput
     * <p>如果指定索引无效（越界或为 null），则回退到第一个有效候选项。</p>
     *
     * @param candidates 候选列表（含 null）
     * @param bestIndex  仲裁选中的索引
     * @return 选中的 GeneratorOutput，全为 null 时返回 null
     */
    public GeneratorOutput selectOutput(List<GeneratorOutput> candidates, int bestIndex) {
        if (bestIndex >= 0 && bestIndex < candidates.size() && candidates.get(bestIndex) != null) {
            return candidates.get(bestIndex);
        }
        for (GeneratorOutput c : candidates) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /**
     * Generator Agent 的输出，包含消息内容和语音参数
     */
    @Data
    @AllArgsConstructor
    public static class GeneratorOutput {
        private String message;
        private VoiceSynthesisParam voiceParams;
    }
}

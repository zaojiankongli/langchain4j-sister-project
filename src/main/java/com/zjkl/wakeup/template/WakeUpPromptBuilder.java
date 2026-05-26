package com.zjkl.wakeup.template;

import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool.UserStateSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提示词模板构建器 — 负责构建 AI prompt 所需的上下文信息。
 */
@Slf4j
@Component
public class WakeUpPromptBuilder {

    /**
     * 构建锚点提示信息
     *
     * @param state 用户状态快照
     * @return 锚点提示字符串
     */
    public String buildAnchorHint(UserStateSnapshot state) {
        StringBuilder sb = new StringBuilder();
        if (state.activeAnchorContext() != null) {
            sb.append(state.activeAnchorContext());
        }
        if (!"无历史锚点事件".equals(state.recentAnchorSummary())
                && !"无已结束的锚点事件".equals(state.recentAnchorSummary())) {
            if (!sb.isEmpty()) sb.append("；");
            sb.append(state.recentAnchorSummary());
        }
        return sb.isEmpty() ? "无特殊事件" : sb.toString();
    }

    /**
     * 构建 fallback 问候消息
     *
     * @param timeContext 时间上下文
     * @return fallback 消息内容
     */
    public String buildFallbackMessage(TimeContextTool.TimeContext timeContext) {
        return timeContext.greeting() + "～今天过得怎么样呀";
    }
}

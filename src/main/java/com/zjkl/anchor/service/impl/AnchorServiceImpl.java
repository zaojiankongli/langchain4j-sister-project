package com.zjkl.anchor.service.impl;

import com.zjkl.anchor.service.AnchorService;
import com.zjkl.emotion.model.EmotionAnchorEvent;
import com.zjkl.emotion.service.EmotionAnchorService;
import com.zjkl.memory.domain.vo.MemoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnchorServiceImpl implements AnchorService {

    private final EmotionAnchorService emotionAnchorService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<MemoryVO> getMilestones(String userId, int offset, int limit, String beginDate, String endDate) {
        List<EmotionAnchorEvent> events = emotionAnchorService.getEventsPaged(userId, offset, limit, beginDate, endDate);
        return events.stream().map(this::toMemoryVO).toList();
    }

    private MemoryVO toMemoryVO(EmotionAnchorEvent event) {
        MemoryVO vo = new MemoryVO();
        vo.setId(event.getId());
        vo.setType("milestone");
        vo.setQuote(event.getTriggerReason());
        // 拼接 summary + aiReflection 作为描述
        StringBuilder desc = new StringBuilder();
        if (event.getSummary() != null) {
            desc.append(event.getSummary());
        }
        if (event.getAiReflection() != null && !event.getAiReflection().isBlank()) {
            if (!desc.isEmpty()) {
                desc.append("\n");
            }
            desc.append(event.getAiReflection());
        }
        vo.setDesc(desc.toString());
        // endType 映射为 mood
        if (event.getEndType() != null) {
            vo.setMood(event.getEndType() == EmotionAnchorEvent.EndType.POSITIVE ? "正向" : "负向");
        }
        if (event.getStartTime() != null) {
            vo.setDate(event.getStartTime().format(DATE_FORMATTER));
            vo.setStartTime(event.getStartTime().format(DATETIME_FORMATTER));
        }
        if (event.getEndTime() != null) {
            vo.setEndTime(event.getEndTime().format(DATETIME_FORMATTER));
        }

        // 锚点专属字段
        vo.setEventTitle(event.getEventTitle());
        vo.setDurationSeconds(event.getDurationSeconds());
        vo.setStartPleasure(event.getStartPleasure());
        vo.setEndPleasure(event.getEndPleasure());
        vo.setPeakPleasure(event.getPeakPleasure());
        vo.setDeltaPleasure(event.getDeltaPleasure());
        vo.setStartArousal(event.getStartArousal());
        vo.setEndArousal(event.getEndArousal());
        vo.setPeakArousal(event.getPeakArousal());
        vo.setDeltaArousal(event.getDeltaArousal());
        vo.setSummary(event.getSummary());
        vo.setEndType(event.getEndType() != null ? event.getEndType().name() : null);
        vo.setAiReflection(event.getAiReflection());
        vo.setHighlightTraits(event.getHighlightTraits());
        vo.setTriggerReason(event.getTriggerReason());
        vo.setEndReason(event.getEndReason());

        return vo;
    }
}

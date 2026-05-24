package com.zjkl.anchor.service;

import com.zjkl.memory.domain.vo.MemoryVO;

import java.util.List;

public interface AnchorService {

    /**
     * 获取重要时刻列表
     */
    List<MemoryVO> getMilestones(String userId, int offset, int limit, String beginDate, String endDate);
}

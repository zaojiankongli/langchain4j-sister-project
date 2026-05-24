package com.zjkl.user.service;

import java.util.List;

/**
 * 用户兴趣标签生成服务接口
 */
public interface InterestTagGenerateService {

    /**
     * 为指定用户生成兴趣标签
     * @param userId 用户 ID
     * @return 生成的标签列表
     */
    List<String> generateTags(String userId);
}
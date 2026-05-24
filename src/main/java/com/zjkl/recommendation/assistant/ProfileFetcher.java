package com.zjkl.recommendation.assistant;

import com.zjkl.recommendation.util.RecommendationConstants;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserProfileMapper;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Non-AI Agent: 从数据库获取用户画像和兴趣标签
 * 零 Token 消耗，纯 Java 操作
 */
public class ProfileFetcher {

    private final UserProfileMapper userProfileMapper;

    public ProfileFetcher(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Agent(value = "从数据库获取用户画像和兴趣标签，输出结构化用户画像字符串", outputKey = RecommendationConstants.OUTPUT_KEY_USER_CONTEXT)
    public String fetchUserProfile(@V("userId") String userId) {
        User user = userProfileMapper.findUserById(userId);
        List<String> tags = userProfileMapper.findInterestTags(userId);

        StringBuilder sb = new StringBuilder();
        if (user != null) {
            sb.append("用户名: ").append(Optional.ofNullable(user.getUsername()).orElse("未知"));
            sb.append("\n性别: ").append(formatGender(user.getGender()));
            sb.append("\n兴趣爱好: ").append(Optional.ofNullable(user.getHobbies()).orElse("未设置"));
            sb.append("\n用户画像: ").append(Optional.ofNullable(user.getUserProfile()).orElse("暂无画像"));
        } else {
            sb.append("用户名: 未知\n用户画像: 暂无画像");
        }
        String tagStr = tags != null && !tags.isEmpty()
                ? tags.stream().collect(Collectors.joining("、"))
                : "暂无标签";
        sb.append("\n兴趣标签: ").append(tagStr);

        return sb.toString();
    }

    private static String formatGender(Integer gender) {
        if (gender == null) return "未知";
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }
}

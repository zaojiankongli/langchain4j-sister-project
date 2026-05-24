package com.zjkl.user.assistant;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 *根据用户画像和聊天记录生成候选兴趣标签
 */
public interface TagGenerator {

    @SystemMessage(fromResource = "prompts/interest-tag-generate-prompt.txt")
    @UserMessage("""
            用户名: {{username}}
            用户画像: {{userProfile}}
            兴趣爱好: {{hobbies}}
            历史标签: {{existingTags}}

            用户记忆数据:
            {{memoryContent}}

            请根据以上信息生成 5-8 个能够反映用户特点的兴趣标签。
            """)
    @Agent(value = "分析用户记忆和画像，生成候选兴趣标签列表", outputKey = "candidateTags")
    String generateTags(@V("username") String username,
                       @V("userProfile") String userProfile,
                       @V("hobbies") String hobbies,
                       @V("existingTags") String existingTags,
                       @V("memoryContent") String memoryContent);
}
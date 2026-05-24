package com.zjkl.emotion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 情感引擎配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "emotion.engine")
public class EmotionEngineConfig {
    
    /**
     * OCEAN 人格参数
     */
    private PersonalityConfig personality = new PersonalityConfig();
    
    /**
     * 衰减率（默认 0.1）
     * 每次交互后情绪的衰减比例
     */
    private Double decayRate = 0.1;
    
    /**
     * 回归率（默认 0.05）
     * 向基础人格回归的速度
     */
    private Double regressionRate = 0.05;
    
    /**
     * 敏感度（默认 0.5）
     * 对外界刺激的响应程度
     */
    private Double sensitivity = 0.5;
    
    /**
     * 人设类型描述
     */
    private String personalityType = "温柔害羞型";
    
    /**
     * OCEAN 人格配置内部类
     */
    @Data
    public static class PersonalityConfig {
        /**
         * 开放性 [-1, 1]
         * 正值：富有想象力、好奇心强
         * 负值：务实、保守
         */
        private Double openness = 0.0;
        
        /**
         * 尽责性 [-1, 1]
         * 正值：自律、有条理
         * 负值：随意、灵活
         */
        private Double conscientiousness = 0.0;
        
        /**
         * 外向性 [-1, 1]
         * 正值：外向、热情
         * 负值：内向、安静（温柔害羞型：-0.5）
         */
        private Double extraversion = -0.5;
        
        /**
         * 宜人性[-1, 1]
         * 正值：温柔、善良（温柔害羞型：0.6）
         * 负值：批判性强
         */
        private Double agreeableness = 0.6;
        
        /**
         * 神经质 [-1, 1]
         * 正值：情绪波动大
         * 负值：情绪稳定（温柔害羞型：-0.2）
         */
        private Double neuroticism = -0.2;
    }
}

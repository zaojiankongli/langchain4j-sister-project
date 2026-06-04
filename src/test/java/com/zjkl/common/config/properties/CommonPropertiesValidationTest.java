package com.zjkl.common.config.properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CommonPropertiesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void appProperties_shouldRejectMissingDefaultImageUrlAndInvalidPromptCacheTtl() {
        AppProperties properties = new AppProperties();
        properties.setDefaultImageUrl("");
        properties.setPromptCacheTtl(0);

        assertFalse(validator.validate(properties).isEmpty(), "app properties must reject blank default image URL and non-positive prompt cache TTL");
    }

    @Test
    void aiProperties_shouldRejectBlankRequiredModelConfiguration() {
        AiProperties properties = new AiProperties();
        properties.setChatApiKey("");
        properties.setChatModelName("");
        properties.setVisionApiKey("");
        properties.setVisionModelName("");

        assertFalse(validator.validate(properties).isEmpty(), "AI properties must reject blank API keys and model names");
    }

    @Test
    void corsProperties_shouldRejectBlankAllowedOrigins() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins("");

        assertFalse(validator.validate(properties).isEmpty(), "CORS allowed origins must not be blank");
    }

    @Test
    void mcpProperties_shouldRejectBlankApiKeys() {
        McpProperties properties = new McpProperties();
        properties.setFirecrawlApiKey("");
        properties.setContext7ApiKey("");

        assertFalse(validator.validate(properties).isEmpty(), "MCP API keys must not be blank");
    }

    @Test
    void milvusProperties_shouldRejectInvalidConnectionSettings() {
        MilvusProperties properties = new MilvusProperties();
        properties.setHost("");
        properties.setPort(0);
        properties.setDatabase("");
        properties.setCollectionName("");
        properties.setUri("");

        assertFalse(validator.validate(properties).isEmpty(), "Milvus connection settings must reject blanks and invalid ports");
    }

    @Test
    void redisProperties_shouldRejectInvalidConnectionSettings() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("");
        properties.setPort(0);
        properties.setDatabase(-1);

        assertFalse(validator.validate(properties).isEmpty(), "Redis connection settings must reject blank host, invalid port, and negative database");
    }

    @Test
    void threadPoolProperties_shouldRejectInvalidPoolSizesAndPrefix() {
        ThreadPoolProperties properties = new ThreadPoolProperties();
        properties.setWebsocketSenderCoreSize(0);
        properties.setWebsocketSenderMaxSize(0);
        properties.setWebsocketSenderQueueCapacity(0);
        properties.setRedissonMinIdle(0);
        properties.setRedissonPoolSize(0);

        assertFalse(validator.validate(properties).isEmpty(), "thread pool settings must reject non-positive sizes and blank prefixes");
    }

    @Test
    void ttsProperties_shouldRejectBlankModelAndVoice() {
        TtsProperties properties = new TtsProperties();
        properties.setModel("");
        properties.setVoice("");

        assertFalse(validator.validate(properties).isEmpty(), "TTS model and voice must not be blank");
    }
}

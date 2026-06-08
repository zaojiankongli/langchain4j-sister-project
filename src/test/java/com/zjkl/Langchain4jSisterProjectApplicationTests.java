package com.zjkl;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.v2.client.MilvusClientV2;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.zjkl.anchor.service.AnchorEventService;
import com.zjkl.memory.service.PromptCacheService;
import com.zjkl.wakeup.tool.UserStateTool;
import com.zjkl.ai.peek.scheduler.PeekScheduler;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "DASHSCOPE_API_KEY=test-key",
        "MAIL_HOST=localhost",
        "MAIL_PORT=587",
        "MAIL_USERNAME=test@example.com",
        "MAIL_PASSWORD=test-password",
        "MILVUS_HOST=localhost",
        "MILVUS_PORT=19530",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "REDIS_DATABASE=0",
        "REDIS_PASSWORD=",
        "MYSQL_HOST=localhost",
        "MYSQL_PORT=3306",
        "MYSQL_USERNAME=root",
        "MYSQL_PASSWORD=root",
        "JWT_SECRET=0123456789abcdef0123456789abcdef",
        "APP_DEFAULT_IMAGE_URL=https://placehold.co/512x512",
        "CORS_ALLOWED_ORIGINS=http://localhost",
        "WEBSOCKET_ALLOWED_ORIGINS=http://localhost",
        "OSS_ENDPOINT=test-endpoint",
        "OSS_ACCESS_KEY_ID=test-key",
        "OSS_ACCESS_KEY_SECRET=test-secret",
        "OSS_BUCKET_NAME=test-bucket",
        "OSS_REGION=test-region",
        "TTS_MODEL=test-model",
        "TTS_VOICE=test-voice",
        "FIRECRAWL_API_KEY=test-firecrawl-key",
        "CONTEXT7_API_KEY=test-context7-key",
        "WANX_REFERENCE_IMAGE_URL=https://placehold.co/512x512"
})
@ActiveProfiles("test")
class Langchain4jSisterProjectApplicationTests {

    @MockBean
    private MilvusEmbeddingStore milvusEmbeddingStore;

    @MockBean
    private MilvusClientV2 milvusClientV2;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private PromptCacheService promptCacheService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private AnchorEventService anchorEventService;

    @MockBean
    private UserStateTool userStateTool;

    @MockBean
    private PeekScheduler peekScheduler;

    @Test
    void contextLoads() {
    }

}

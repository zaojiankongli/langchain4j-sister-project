package com.zjkl.common.config;

import com.zjkl.common.config.properties.ThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步任务线程池配置
 * <p>
 * 为 @Async 注解提供可配置的线程池。
 * 池大小通过 app.thread-pool.async-* 控制，适应不同核心数的服务器。
 * 虚拟线程默认启用，但线程池作为后备和批处理任务的资源控制器。
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final int IMAGE_TASK_CORE_POOL_SIZE = 1;
    private static final int IMAGE_TASK_MAX_POOL_SIZE = 2;
    private static final int IMAGE_TASK_QUEUE_CAPACITY = 20;

    private final ThreadPoolProperties threadPoolProperties;

    public AsyncConfig(ThreadPoolProperties threadPoolProperties) {
        this.threadPoolProperties = threadPoolProperties;
    }

    @Override
    @Bean(name = "asyncTaskExecutor")
    public Executor getAsyncExecutor() {
        log.info("异步任务执行器初始化：使用虚拟线程执行器");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean("llmTaskExecutor")
    public Executor llmTaskExecutor() {
        return buildBoundedExecutor("llm-task-",
                threadPoolProperties.getLlmCoreSize(),
                threadPoolProperties.getLlmMaxSize(),
                threadPoolProperties.getLlmQueueCapacity(),
                "LLM 外部调用");
    }

    @Bean("ttsTaskExecutor")
    public Executor ttsTaskExecutor() {
        return buildBoundedExecutor("tts-task-",
                threadPoolProperties.getTtsCoreSize(),
                threadPoolProperties.getTtsMaxSize(),
                threadPoolProperties.getTtsQueueCapacity(),
                "TTS 外部调用");
    }

    @Bean("milvusTaskExecutor")
    public Executor milvusTaskExecutor() {
        return buildBoundedExecutor("milvus-task-",
                threadPoolProperties.getMilvusCoreSize(),
                threadPoolProperties.getMilvusMaxSize(),
                threadPoolProperties.getMilvusQueueCapacity(),
                "Milvus 外部调用");
    }

    @Bean("imageTaskExecutor")
    public Executor imageTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(IMAGE_TASK_CORE_POOL_SIZE);
        executor.setMaxPoolSize(IMAGE_TASK_MAX_POOL_SIZE);
        executor.setQueueCapacity(IMAGE_TASK_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("image-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("图片任务线程池初始化：core={}, max={}, queue={}",
                IMAGE_TASK_CORE_POOL_SIZE, IMAGE_TASK_MAX_POOL_SIZE, IMAGE_TASK_QUEUE_CAPACITY);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("异步任务执行异常：method={}", method.getName(), ex);
    }

    private Executor buildBoundedExecutor(String threadNamePrefix,
                                          int corePoolSize,
                                          int maxPoolSize,
                                          int queueCapacity,
                                          String label) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("{}线程池初始化：core={}, max={}, queue={}", label, corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}

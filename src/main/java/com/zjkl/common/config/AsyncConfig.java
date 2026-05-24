package com.zjkl.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步线程池配置
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class AsyncConfig {
    
    /**
     * 图片任务专用线程池
     *
     */
    @Bean("imageTaskExecutor")
    public SimpleAsyncTaskExecutor imageTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setThreadNamePrefix("image-task-");
        executor.setVirtualThreads(true);  // JDK 21+ 启用虚拟线程
        log.info("初始化图片任务----- 虚拟线程池");
        return executor;
    }
    
    /**
     * Spring MVC 异步任务专用线程池
     *
     */
    @Bean("taskExecutor")
    public SimpleAsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setThreadNamePrefix("mvc-async-");
        executor.setVirtualThreads(true);
        log.info("初始化 Web 层专用线程池----- 虚拟线程池");
        return executor;
    }
}

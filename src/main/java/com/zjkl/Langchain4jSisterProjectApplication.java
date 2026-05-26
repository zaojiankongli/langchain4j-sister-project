package com.zjkl;


import com.zjkl.emotion.config.EmotionEngineConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
@EnableConfigurationProperties(EmotionEngineConfig.class)
public class Langchain4jSisterProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(Langchain4jSisterProjectApplication.class, args);
    }

}

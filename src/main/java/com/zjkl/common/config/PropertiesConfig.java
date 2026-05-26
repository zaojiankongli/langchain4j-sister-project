package com.zjkl.common.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/**
 * 配置属性扫描入口
 * 启用 @ConfigurationPropertiesScan 自动注册所有 Properties 类
 */
@Configuration
@ConfigurationPropertiesScan("com.zjkl.common.config.properties")
public class PropertiesConfig {

}

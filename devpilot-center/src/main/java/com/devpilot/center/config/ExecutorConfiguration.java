package com.devpilot.center.config;

import com.devpilot.executor.config.ExecutorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExecutorProperties.class)
public class ExecutorConfiguration {
}

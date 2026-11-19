package com.devpilot.repository.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RepositoryProperties.class)
public class RepositoryAutoConfiguration {
}

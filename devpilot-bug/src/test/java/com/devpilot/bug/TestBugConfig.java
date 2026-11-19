package com.devpilot.bug;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@Configuration
@EntityScan(basePackages = "com.devpilot.bug.domain")
@EnableJpaRepositories(basePackages = "com.devpilot.bug.domain")
public class TestBugConfig {
}

package com.devpilot.task;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test-only bootstrap so @DataJpaTest can locate the module's entities and
 * repositories without depending on the center application.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.devpilot.task.domain")
@EnableJpaRepositories(basePackages = "com.devpilot.task.repository")
class TestJpaConfig {
}

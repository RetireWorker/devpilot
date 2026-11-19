package com.devpilot.center;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.devpilot")
@EntityScan(basePackages = "com.devpilot")
@EnableJpaRepositories(basePackages = "com.devpilot")
@EnableScheduling
public class DevpilotCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevpilotCenterApplication.class, args);
    }
}

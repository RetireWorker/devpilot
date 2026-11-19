package com.devpilot.ai.config;

import com.devpilot.ai.AiProperties;
import com.devpilot.ai.HeuristicBugAnalyzer;
import com.devpilot.ai.LlmBugAnalyzer;
import com.devpilot.common.api.AiAnalysisPort;
import com.devpilot.common.error.ValidationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    /**
     * Only the analysis port is provided here. Task-plan wiring stays in the
     * center so this module does not depend on the task module.
     */
    @Bean
    public AiAnalysisPort aiAnalysisPort(AiProperties properties, HeuristicBugAnalyzer heuristic) {
        if (properties.isEnabled()) {
            if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
                throw new ValidationException("ai.llm.enabled=true 但未配置 api-key");
            }
            var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
            factory.setReadTimeout((int) properties.getReadTimeout().toMillis());
            var client = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(factory)
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .build();
            return new LlmBugAnalyzer(client, properties, heuristic);
        }
        return heuristic::analyze;
    }
}

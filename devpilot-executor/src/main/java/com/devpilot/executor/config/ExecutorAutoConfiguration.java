package com.devpilot.executor.config;

import com.devpilot.common.api.AgentExecutor;
import com.devpilot.common.api.NodeCommandExecutor;
import com.devpilot.executor.LocalOrcaExecutor;
import com.devpilot.executor.RemoteOrcaExecutor;
import com.devpilot.executor.RoutingOrcaExecutor;
import com.devpilot.executor.client.ExecutorTransport;
import com.devpilot.executor.client.HttpExecutorTransport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(ExecutorProperties.class)
public class ExecutorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "ai.executor.mode", havingValue = "local", matchIfMissing = true)
    public LocalOrcaExecutor localOrcaExecutor(ExecutorProperties properties) {
        return new LocalOrcaExecutor(transport(properties.getLocal()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "ai.executor.mode", havingValue = "remote")
    public RemoteOrcaExecutor remoteOrcaExecutor(ExecutorProperties properties) {
        return new RemoteOrcaExecutor(transport(properties.getRemote()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "ai.executor.mode", havingValue = "routing")
    public RoutingOrcaExecutor routingOrcaExecutor(ExecutorProperties properties) {
        Map<String, com.devpilot.executor.OrcaExecutor> local = new LinkedHashMap<>();
        if (properties.getLocal().isEnabled()) {
            local.put("local", new LocalOrcaExecutor(transport(properties.getLocal())));
        }
        if (properties.getRemote().isEnabled()) {
            local.put("remote", new RemoteOrcaExecutor(transport(properties.getRemote())));
        }
        return new RoutingOrcaExecutor(local, properties);
    }

    private ExecutorTransport transport(ExecutorProperties.Endpoint endpoint) {
        return new HttpExecutorTransport(endpoint);
    }
}

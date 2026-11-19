package com.devpilot.center.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ai.devpilot")
public class DevpilotProperties {

    /**
     * Phase 1 deliberately keeps Git push / SVN dcommit / deployment disabled.
     */
    private boolean deliveryEnabled = false;
    private boolean deploymentEnabled = false;
    private int maxRepairRounds = 2;
    private Duration executionPollInterval = Duration.ofSeconds(2);
    private Duration executionTimeout = Duration.ofMinutes(15);

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    public void setDeliveryEnabled(boolean deliveryEnabled) {
        this.deliveryEnabled = deliveryEnabled;
    }

    public boolean isDeploymentEnabled() {
        return deploymentEnabled;
    }

    public void setDeploymentEnabled(boolean deploymentEnabled) {
        this.deploymentEnabled = deploymentEnabled;
    }

    public int getMaxRepairRounds() {
        return maxRepairRounds;
    }

    public void setMaxRepairRounds(int maxRepairRounds) {
        this.maxRepairRounds = maxRepairRounds;
    }

    public Duration getExecutionPollInterval() {
        return executionPollInterval;
    }

    public void setExecutionPollInterval(Duration executionPollInterval) {
        this.executionPollInterval = executionPollInterval;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }
}

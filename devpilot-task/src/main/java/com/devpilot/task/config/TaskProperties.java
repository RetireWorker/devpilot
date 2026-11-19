package com.devpilot.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.task")
public class TaskProperties {

    private int maxRepairRounds = 2;
    private boolean deliveryEnabled = false;

    public int getMaxRepairRounds() {
        return maxRepairRounds;
    }

    public void setMaxRepairRounds(int maxRepairRounds) {
        this.maxRepairRounds = maxRepairRounds;
    }

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    public void setDeliveryEnabled(boolean deliveryEnabled) {
        this.deliveryEnabled = deliveryEnabled;
    }
}

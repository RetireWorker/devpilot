package com.devpilot.executor.config;

import com.devpilot.common.domain.ExecutorMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("ai.executor")
public class ExecutorProperties {

    private ExecutorMode mode = ExecutorMode.LOCAL;
    private Endpoint local = new Endpoint(false, "http://127.0.0.1:18080", "", Duration.ofSeconds(3), Duration.ofSeconds(30));
    private Endpoint remote = new Endpoint(false, "", "", Duration.ofSeconds(5), Duration.ofSeconds(30));
    private String defaultTarget = "local";
    private Map<String, String> routing = new LinkedHashMap<>();

    public ExecutorMode getMode() {
        return mode;
    }

    public void setMode(ExecutorMode mode) {
        this.mode = mode;
    }

    public Endpoint getLocal() {
        return local;
    }

    public void setLocal(Endpoint local) {
        this.local = local;
    }

    public Endpoint getRemote() {
        return remote;
    }

    public void setRemote(Endpoint remote) {
        this.remote = remote;
    }

    public String getDefaultTarget() {
        return defaultTarget;
    }

    public void setDefaultTarget(String defaultTarget) {
        this.defaultTarget = defaultTarget;
    }

    public Map<String, String> getRouting() {
        return routing;
    }

    public void setRouting(Map<String, String> routing) {
        this.routing = routing;
    }

    public static class Endpoint {
        private boolean enabled;
        private String endpoint;
        private String token;
        private Duration connectTimeout;
        private Duration readTimeout;

        public Endpoint() {
        }

        public Endpoint(boolean enabled, String endpoint, String token,
                        Duration connectTimeout, Duration readTimeout) {
            this.enabled = enabled;
            this.endpoint = endpoint;
            this.token = token;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}

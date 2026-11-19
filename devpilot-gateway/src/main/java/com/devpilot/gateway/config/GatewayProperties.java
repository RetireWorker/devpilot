package com.devpilot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("ai.gateway")
public class GatewayProperties {

    /**
     * Execution is disabled by default: an enabled gateway without a
     * configured Orca binary must not silently spawn anything else.
     */
    private boolean enabled = false;
    private String workspaceRoot = "./data/workspaces";
    private String stateDir = "./data/gateway";
    private String token = "";
    private String orcaBinary = "orca";
    private Duration defaultTimeout = Duration.ofMinutes(10);
    private long maxLogBytes = 10_000_000L;
    private Map<String, List<String>> agentTemplates = Map.of();
    private Map<String, List<String>> commandTemplates = Map.of();

    /**
     * {@code orca} drives the real Orca CLI (supervised workers). {@code template}
     * keeps the legacy argv-template path for other Agent runtimes and tests.
     */
    private String agentRunner = "orca";
    private String defaultAgentId = "claude";
    private Map<String, String> agentIds = Map.of();
    private String workerModel = "";
    private Duration workerPollInterval = Duration.ofSeconds(5);
    private Duration workerStartTimeout = Duration.ofSeconds(120);
    /** A chat worker can stay open after its turn; idle this long with a
     * structured final answer counts as completion. */
    private Duration workerIdleSettle = Duration.ofSeconds(20);
    /** How long to keep reading the transcript after the worker settles. */
    private Duration workerDrainWindow = Duration.ofSeconds(5);
    private int maxConcurrentWorkers = 4;
    private boolean registerWorkspace = true;
    private boolean hideWorkspaceWhenDone = true;
    private boolean autoCommit = true;
    private String commitAuthorName = "devpilot-agent";
    private String commitAuthorEmail = "devpilot@localhost";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getStateDir() {
        return stateDir;
    }

    public void setStateDir(String stateDir) {
        this.stateDir = stateDir;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOrcaBinary() {
        return orcaBinary;
    }

    public void setOrcaBinary(String orcaBinary) {
        this.orcaBinary = orcaBinary;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public long getMaxLogBytes() {
        return maxLogBytes;
    }

    public void setMaxLogBytes(long maxLogBytes) {
        this.maxLogBytes = maxLogBytes;
    }

    public Map<String, List<String>> getAgentTemplates() {
        return agentTemplates;
    }

    public void setAgentTemplates(Map<String, List<String>> agentTemplates) {
        this.agentTemplates = agentTemplates;
    }

    public Map<String, List<String>> getCommandTemplates() {
        return commandTemplates;
    }

    public void setCommandTemplates(Map<String, List<String>> commandTemplates) {
        this.commandTemplates = commandTemplates;
    }

    public String getAgentRunner() {
        return agentRunner;
    }

    public void setAgentRunner(String agentRunner) {
        this.agentRunner = agentRunner;
    }

    public String getDefaultAgentId() {
        return defaultAgentId;
    }

    public void setDefaultAgentId(String defaultAgentId) {
        this.defaultAgentId = defaultAgentId;
    }

    public Map<String, String> getAgentIds() {
        return agentIds;
    }

    public void setAgentIds(Map<String, String> agentIds) {
        this.agentIds = agentIds;
    }

    public String getWorkerModel() {
        return workerModel;
    }

    public void setWorkerModel(String workerModel) {
        this.workerModel = workerModel;
    }

    public Duration getWorkerPollInterval() {
        return workerPollInterval;
    }

    public void setWorkerPollInterval(Duration workerPollInterval) {
        this.workerPollInterval = workerPollInterval;
    }

    public Duration getWorkerStartTimeout() {
        return workerStartTimeout;
    }

    public void setWorkerStartTimeout(Duration workerStartTimeout) {
        this.workerStartTimeout = workerStartTimeout;
    }

    public Duration getWorkerIdleSettle() {
        return workerIdleSettle;
    }

    public void setWorkerIdleSettle(Duration workerIdleSettle) {
        this.workerIdleSettle = workerIdleSettle;
    }

    public Duration getWorkerDrainWindow() {
        return workerDrainWindow;
    }

    public void setWorkerDrainWindow(Duration workerDrainWindow) {
        this.workerDrainWindow = workerDrainWindow;
    }

    public int getMaxConcurrentWorkers() {
        return maxConcurrentWorkers;
    }

    public void setMaxConcurrentWorkers(int maxConcurrentWorkers) {
        this.maxConcurrentWorkers = maxConcurrentWorkers;
    }

    public boolean isRegisterWorkspace() {
        return registerWorkspace;
    }

    public void setRegisterWorkspace(boolean registerWorkspace) {
        this.registerWorkspace = registerWorkspace;
    }

    public boolean isHideWorkspaceWhenDone() {
        return hideWorkspaceWhenDone;
    }

    public void setHideWorkspaceWhenDone(boolean hideWorkspaceWhenDone) {
        this.hideWorkspaceWhenDone = hideWorkspaceWhenDone;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public String getCommitAuthorName() {
        return commitAuthorName;
    }

    public void setCommitAuthorName(String commitAuthorName) {
        this.commitAuthorName = commitAuthorName;
    }

    public String getCommitAuthorEmail() {
        return commitAuthorEmail;
    }

    public void setCommitAuthorEmail(String commitAuthorEmail) {
        this.commitAuthorEmail = commitAuthorEmail;
    }
}

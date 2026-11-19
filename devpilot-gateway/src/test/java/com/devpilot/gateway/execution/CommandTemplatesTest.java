package com.devpilot.gateway.execution;

import com.devpilot.common.api.AgentRole;
import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandTemplatesTest {

    @Test
    void substitutesAllowedParameter() {
        var properties = new GatewayProperties();
        properties.setCommandTemplates(Map.of("echo", List.of("echo", "{text}")));
        var templates = new CommandTemplates(properties);

        assertThat(templates.command("echo", Map.of("text", "hello-1")))
                .containsExactly("echo", "hello-1");
    }

    @Test
    void rejectsUnknownCommand() {
        var templates = new CommandTemplates(new GatewayProperties());

        assertThatThrownBy(() -> templates.command("rm-rf", Map.of()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsShellMetacharacters() {
        var properties = new GatewayProperties();
        properties.setCommandTemplates(Map.of("echo", List.of("echo", "{text}")));
        var templates = new CommandTemplates(properties);

        assertThatThrownBy(() -> templates.command("echo", Map.of("text", "x; rm -rf /")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsMissingParameter() {
        var properties = new GatewayProperties();
        properties.setAgentTemplates(Map.of(AgentRole.JAVA_DEVELOPER.name(),
                List.of("orca", "run", "--task", "{taskId}")));
        var templates = new CommandTemplates(properties);

        assertThatThrownBy(() -> templates.agent(AgentRole.JAVA_DEVELOPER, Map.of()))
                .isInstanceOf(ValidationException.class);
    }
}

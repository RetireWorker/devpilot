package com.devpilot.gateway.execution;

import com.devpilot.common.api.AgentRole;
import com.devpilot.common.error.ValidationException;
import com.devpilot.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves a named command to an argv template. Unknown names and unsafe
 * parameter values fail closed; there is no free-form shell execution.
 */
@Component
public class CommandTemplates {

    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._/@:+-]{1,200}$");

    private final GatewayProperties properties;

    public CommandTemplates(GatewayProperties properties) {
        this.properties = properties;
    }

    public List<String> command(String commandKey, Map<String, String> parameters) {
        List<String> template = properties.getCommandTemplates().get(commandKey);
        if (template == null || template.isEmpty()) {
            throw new ValidationException("unknown command key: " + commandKey);
        }
        return substitute(template, parameters);
    }

    public List<String> agent(AgentRole role, Map<String, String> parameters) {
        List<String> template = properties.getAgentTemplates().get(role.name());
        if (template == null || template.isEmpty()) {
            throw new ValidationException("no agent template configured for role: " + role);
        }
        return substitute(template, parameters);
    }

    private List<String> substitute(List<String> template, Map<String, String> parameters) {
        var values = parameters == null ? Map.<String, String>of() : parameters;
        return template.stream()
                .map(token -> substituteToken(token, values))
                .toList();
    }

    private String substituteToken(String token, Map<String, String> parameters) {
        if (!token.contains("{")) {
            return token;
        }
        var matcher = Pattern.compile("\\{([A-Za-z0-9_.-]+)}").matcher(token);
        var result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = parameters.get(name);
            if (value == null) {
                throw new ValidationException("missing template parameter: " + name);
            }
            validate(value);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static void validate(String value) {
        if (value == null || !SAFE_VALUE.matcher(value).matches()) {
            throw new ValidationException("unsafe parameter value rejected");
        }
        if (value.contains("..")) {
            throw new ValidationException("path traversal is not allowed");
        }
    }
}

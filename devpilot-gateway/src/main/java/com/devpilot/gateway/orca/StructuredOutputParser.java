package com.devpilot.gateway.orca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the last fenced JSON block from an agent transcript. The spec
 * requires exactly one such block as the final answer, but a worker may also
 * print JSON while working, so the last parseable object wins.
 */
@Component
public class StructuredOutputParser {

    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```", Pattern.MULTILINE);

    private final ObjectMapper objectMapper;

    public StructuredOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<JsonNode> lastJsonBlock(List<OrcaCli.Message> messages) {
        JsonNode found = null;
        for (OrcaCli.Message message : messages) {
            if (message.role() != null && message.role().equalsIgnoreCase("user")) {
                continue;
            }
            Matcher matcher = FENCED_JSON.matcher(message.text());
            while (matcher.find()) {
                try {
                    JsonNode parsed = objectMapper.readTree(matcher.group(1));
                    if (parsed.isObject()) {
                        found = parsed;
                    }
                } catch (Exception ignored) {
                    // Not valid JSON despite the fence; keep scanning.
                }
            }
        }
        return Optional.ofNullable(found);
    }
}
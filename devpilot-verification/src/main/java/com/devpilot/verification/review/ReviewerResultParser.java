package com.devpilot.verification.review;

import com.devpilot.common.api.VerificationResultView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses structured reviewer output. Unknown or malformed payloads fail closed
 * so a broken reviewer cannot silently approve a change.
 */
public final class ReviewerResultParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReviewerResultParser() {
    }

    public static VerificationResultView parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            // A missing, null, or contract-free answer must not be read as approval.
            if (root == null || !root.isObject() || (!root.has("passed") && !root.has("blocking"))) {
                return new VerificationResultView("REVIEW", false,
                        List.of("审查器未输出结构化结论"),
                        List.of(), List.of(), "reviewer output missing");
            }
            List<String> blocking = strings(root, "blocking");
            List<String> warnings = strings(root, "warnings");
            List<String> risks = strings(root, "risks");
            boolean passed = root.path("passed").asBoolean(blocking.isEmpty());
            String summary = root.path("summary").asText("reviewer result");
            return new VerificationResultView("REVIEW", passed, blocking, warnings, risks, summary);
        } catch (Exception e) {
            return new VerificationResultView("REVIEW", false,
                    List.of("审查器输出无法解析: " + e.getMessage()),
                    List.of(), List.of(), "reviewer output malformed");
        }
    }

    private static List<String> strings(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        });
        return List.copyOf(values);
    }
}

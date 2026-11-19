package com.devpilot.ai;

import com.devpilot.common.api.AiAnalysisPort;
import com.devpilot.common.api.BugAnalysisInput;
import com.devpilot.common.api.BugAnalysisSummary;
import com.devpilot.common.error.DevpilotException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat client. Malformed or failed responses throw rather
 * than silently falling back, so configuration drift is visible.
 */
public class LlmBugAnalyzer implements AiAnalysisPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT = """
            你是 BUG 分析 Agent。只依据给定 BUG 信息输出 JSON，不得编造模块或根因。
            字段：bugId, title, description, expectedResult, actualResult, problemType,
            severity, impactScope[], suspectedModules[], errorLogSummary,
            missingInfo[], reproducibility。
            """;

    private final RestClient client;
    private final AiProperties properties;
    private final HeuristicBugAnalyzer fallback;

    public LlmBugAnalyzer(RestClient client, AiProperties properties, HeuristicBugAnalyzer fallback) {
        this.client = client;
        this.properties = properties;
        this.fallback = fallback;
    }

    @Override
    public BugAnalysisSummary analyzeBug(BugAnalysisInput input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", toPrompt(input))));
        try {
            var response = client.post().uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return MAPPER.readValue(extractContent(response),
                    new TypeReference<BugAnalysisSummary>() { });
        } catch (RestClientException e) {
            throw new DevpilotException("模型调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new DevpilotException("模型返回无法解析: " + e.getMessage(), e);
        }
    }

    private String toPrompt(BugAnalysisInput input) {
        return """
                bugId: %s
                title: %s
                description: %s
                reproduction: %s
                expected: %s
                actual: %s
                environment: %s
                version: %s
                severity: %s
                productionIssue: %s
                errorLog:
                %s
                """.formatted(input.bugId(), input.title(), input.description(),
                input.reproductionSteps(), input.expectedResult(), input.actualResult(),
                input.environment(), input.version(), input.severity(),
                input.productionIssue(), input.errorLog());
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            throw new DevpilotException("模型返回为空");
        }
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new DevpilotException("模型返回缺少 choices");
        }
        var message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || message.get("content") == null) {
            throw new DevpilotException("模型返回缺少 message.content");
        }
        return String.valueOf(message.get("content"));
    }
}

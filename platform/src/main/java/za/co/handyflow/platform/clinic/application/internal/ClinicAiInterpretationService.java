package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls the Anthropic Messages API server-side. Deliberately its own small,
 * focused class — same shape as EmailService/SmsSender/FileStorageService in
 * this codebase — rather than folding the HTTP call into ClinicLabService,
 * so the "how do we reach Claude" concern stays separate from lab-result
 * domain logic.
 * <p>
 * FIX: the frontend previously called
 * https://api.anthropic.com/v1/messages directly from the browser with no
 * API key attached at all (confirmed — the request payload had no
 * x-api-key/Authorization header). Even with a key added, shipping an
 * Anthropic key to every browser that loads this page would leak it
 * publicly the moment anyone opened dev tools. This class exists so the key
 * only ever lives on the server, read from configuration
 * (anthropic.api-key — set via the ANTHROPIC_API_KEY environment variable
 * or application.yml, never committed to source).
 */
@Slf4j
@Component
public class ClinicAiInterpretationService {

    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 1000;

    @Value("${anthropic.api-key:}")
    private String apiKey;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.anthropic.com/v1/messages")
            .build();

    /**
     * @return the plain-language interpretation text, or null if the API
     * key isn't configured or the call fails — callers should treat a null
     * result as "interpretation unavailable right now," not as an error
     * that should break the surrounding lab-result operation.
     */
    @SuppressWarnings("unchecked")
    public String interpret(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Anthropic API key not configured (anthropic.api-key) — skipping AI interpretation");
            return null;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", MAX_TOKENS,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            Map<String, Object> response = restClient.post()
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) return null;
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content == null || content.isEmpty()) return null;
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            log.error("Anthropic interpretation call failed: {}", e.getMessage(), e);
            return null;
        }
    }
}
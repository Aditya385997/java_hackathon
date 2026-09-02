package com.aditya.app.dispatch.routing.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini over plain HTTP with Spring's {@link RestClient} — no AI framework and no
 * new dependency; {@code spring-boot-starter-web} already brings both the client and Jackson.
 *
 * <p>The read timeout is the load-bearing setting: it is what bounds how long a routing call
 * can wait on a model before the strategy gives up and falls back.
 */
@Component
public class GeminiLLMGateway implements LLMGateway {

    private static final Logger log = LoggerFactory.getLogger(GeminiLLMGateway.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** Gemini finished the answer. Anything else means the text we got is not the whole answer. */
    private static final String FINISH_REASON_OK = "STOP";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;
    private final int maxOutputTokens;

    public GeminiLLMGateway(ObjectMapper objectMapper,
                            @Value("${llm.base-url}") String baseUrl,
                            @Value("${llm.model}") String model,
                            @Value("${llm.api-key}") String apiKey,
                            @Value("${llm.timeout}") Duration timeout,
                            @Value("${llm.max-output-tokens}") int maxOutputTokens) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.apiKey = apiKey;
        this.maxOutputTokens = maxOutputTokens;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(timeout)))
                .build();
        log.info("Gemini gateway ready: model '{}', timeout {}, maxOutputTokens {}, credential {}",
                model, timeout, maxOutputTokens,
                hasCredential() ? "present" : "ABSENT (ai routing will fall back)");
    }

    /** Package-private for the constructor log and for tests to assert the no-key short circuit. */
    boolean hasCredential() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(LlmPrompt prompt) {
        if (!hasCredential()) {
            // Fail before opening a socket: an unconfigured demo should fall back instantly,
            // not after burning the read timeout.
            throw new LlmGatewayException("no API key configured (set GEMINI_API_KEY)");
        }

        byte[] raw;
        try {
            raw = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody(prompt))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            // Covers timeouts, connection failures, 4xx quota/auth and 5xx provider errors.
            throw new LlmGatewayException("Gemini call failed: " + e.getMessage(), e);
        }

        return extractText(parse(raw));
    }

    private Map<String, Object> requestBody(LlmPrompt prompt) {
        return Map.of(
                "systemInstruction", Map.of("parts",
                        List.of(Map.of("text", prompt.systemInstruction()))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt.userPrompt())))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0,
                        "maxOutputTokens", maxOutputTokens));
    }

    /**
     * Parses the envelope from raw bytes instead of letting the HTTP layer pick a converter by
     * Content-Type. Gemini has been observed returning a JSON body labelled
     * {@code application/octet-stream}, which no JSON converter will read — but the payload of
     * this endpoint is JSON whatever it is labelled, so the label is not worth trusting.
     *
     * <p>Bytes rather than String deliberately: {@code RestClient}'s default
     * {@code StringHttpMessageConverter} falls back to ISO-8859-1 when a response declares no
     * charset, which would corrupt non-ASCII characters in the model's reasoning. That reasoning
     * is stored verbatim, so it must survive the wire exactly. Jackson detects the encoding from
     * the byte stream itself.
     */
    private JsonNode parse(byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new LlmGatewayException("Gemini returned an empty body");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new LlmGatewayException(
                    "Gemini returned a body that is not JSON: " + e.getMessage(), e);
        }
    }

    /** Navigates the candidates envelope defensively — a shape change is a gateway failure. */
    private String extractText(JsonNode response) {
        if (response == null) {
            throw new LlmGatewayException("Gemini returned an empty body");
        }
        JsonNode candidate = response.path("candidates").path(0);
        requireCompleteAnswer(candidate);

        JsonNode text = candidate.path("content").path("parts").path(0).path("text");
        if (!text.isTextual() || text.asText().isBlank()) {
            throw new LlmGatewayException("Gemini response carried no text in candidates[0]");
        }
        return text.asText();
    }

    /**
     * Rejects a partial answer before it can be mistaken for a complete one.
     *
     * <p>Without this, a response cut off mid-string reaches the strategy as a syntactically
     * broken payload and is reported as "not parseable JSON" — which names the symptom and
     * hides the cause. MAX_TOKENS in particular is a configuration problem, not a bad model:
     * on Gemini 3.x {@code maxOutputTokens} is a combined budget for thinking *and* answer
     * tokens, so a budget sized for the answer alone is spent before the JSON is closed.
     *
     * <p>An absent finishReason is treated as fine — only an explicit non-STOP value fails.
     */
    private void requireCompleteAnswer(JsonNode candidate) {
        JsonNode finishReason = candidate.path("finishReason");
        if (!finishReason.isTextual() || FINISH_REASON_OK.equals(finishReason.asText())) {
            return;
        }
        String reason = finishReason.asText();
        String advice = "MAX_TOKENS".equals(reason)
                ? " — the answer was truncated; raise llm.max-output-tokens (it also has to"
                        + " cover the model's thinking tokens)"
                : "";
        throw new LlmGatewayException(
                "Gemini stopped before finishing: finishReason " + reason + advice);
    }
}

package com.aditya.app.dispatch;

import com.aditya.app.dispatch.routing.ai.GeminiLLMGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aditya.app.dispatch.routing.ai.LlmGatewayException;
import com.aditya.app.dispatch.routing.ai.LlmPrompt;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real loopback HTTP server (JDK built-in, no new dependency) so the wire
 * format — path, credential header, request body and response envelope — is actually
 * exercised rather than assumed.
 */
class GeminiLLMGatewayTest {

    private static final LlmPrompt PROMPT = new LlmPrompt("You route deliveries.", "Who takes ORD-001?");

    private HttpServer server;
    private final List<String> requestBodies = new ArrayList<>();
    private final List<String> apiKeyHeaders = new ArrayList<>();
    private final List<String> acceptHeaders = new ArrayList<>();
    private volatile String responseContentType = "application/json";
    private final AtomicInteger status = new AtomicInteger(200);
    private volatile String responseBody = "{}";
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        apiKeyHeaders.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
        acceptHeaders.add(exchange.getRequestHeaders().getFirst("Accept"));
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", responseContentType);
        exchange.sendResponseHeaders(status.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private GeminiLLMGateway gateway(String apiKey) {
        return gateway(apiKey, 2048);
    }

    private GeminiLLMGateway gateway(String apiKey, int maxOutputTokens) {
        return new GeminiLLMGateway(new ObjectMapper(), baseUrl, "gemini-3.6-flash", apiKey,
                Duration.ofSeconds(5), maxOutputTokens);
    }

    @Test
    void extractsTheModelTextFromTheCandidatesEnvelope() {
        responseBody = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\"recommendedAgentId\\":\\"AGT-002\\"}"}]}}]}""";

        String text = gateway("test-key").complete(PROMPT);

        assertThat(text).isEqualTo("{\"recommendedAgentId\":\"AGT-002\"}");
        assertThat(apiKeyHeaders).containsExactly("test-key");
        assertThat(requestBodies).singleElement().satisfies(body -> assertThat(body)
                .contains("systemInstruction")
                .contains("You route deliveries.")
                .contains("Who takes ORD-001?")
                .contains("\"responseMimeType\":\"application/json\""));
    }

    /**
     * The second real-provider failure: Gemini returned a JSON body labelled
     * application/octet-stream. Binding straight to JsonNode made Spring pick a converter by
     * Content-Type and fail with "Error while extracting response ... content type
     * [application/octet-stream]" before the payload was ever looked at.
     */
    @Test
    void readsAJsonBodyThatTheProviderLabelledOctetStream() {
        responseContentType = "application/octet-stream";
        responseBody = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\"recommendedAgentId\\":\\"AGT-002\\"}"}]}}]}""";

        assertThat(gateway("test-key").complete(PROMPT))
                .isEqualTo("{\"recommendedAgentId\":\"AGT-002\"}");
    }

    @Test
    void asksForJsonSoTheProviderHasNoReasonToGuess() {
        responseBody = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{}"}]}}]}""";

        gateway("test-key").complete(PROMPT);

        assertThat(acceptHeaders).singleElement()
                .satisfies(accept -> assertThat(accept).contains("application/json"));
    }

    /**
     * Reasoning is persisted verbatim, so it has to survive the wire byte-for-byte. Reading the
     * body as String would decode an unlabelled charset as ISO-8859-1 and mangle this.
     */
    @Test
    void preservesNonAsciiTextWhenTheResponseDeclaresNoCharset() {
        responseContentType = "application/octet-stream";
        responseBody = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Koramangala — Indiranagar · ₹450 · Ananya Iyer"}]}}]}""";

        assertThat(gateway("test-key").complete(PROMPT))
                .isEqualTo("Koramangala — Indiranagar · ₹450 · Ananya Iyer");
    }

    @Test
    void failsWhenTheBodyIsNotJsonAtAll() {
        responseContentType = "text/html";
        responseBody = "<html><body>502 Bad Gateway</body></html>";

        assertThatThrownBy(() -> gateway("test-key").complete(PROMPT))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("not JSON");
    }

    @Test
    void wrapsAProviderErrorInLlmGatewayException() {
        status.set(429);
        responseBody = """
                {"error":{"code":429,"message":"Quota exceeded"}}""";

        assertThatThrownBy(() -> gateway("test-key").complete(PROMPT))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("Gemini call failed");
    }

    @Test
    void failsWhenTheResponseCarriesNoTextInTheEnvelope() {
        responseBody = "{\"candidates\":[]}";

        assertThatThrownBy(() -> gateway("test-key").complete(PROMPT))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("no text");
    }

    /**
     * The bug the real Gemini smoke test hit: on Gemini 3.x, maxOutputTokens is a combined
     * budget for thinking *and* answer tokens, so a small budget is spent on reasoning and
     * the JSON is cut off mid-string. The gateway used to hand that fragment downstream,
     * where it surfaced as "not parseable JSON" — the symptom, not the cause.
     */
    @Test
    void rejectsATruncatedAnswerWithAnActionableReasonInsteadOfPassingItOn() {
        responseBody = """
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"{\\"recommendedAgentId\\":\\"AGT-002\\",\\"confidence\\":0.8,\\"reasoning\\":\\"Rahul is idle and"}]}}]}""";

        assertThatThrownBy(() -> gateway("test-key").complete(PROMPT))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("MAX_TOKENS")
                .hasMessageContaining("llm.max-output-tokens")
                .as("the caller must never receive the partial text")
                .hasMessageNotContaining("Rahul is idle and");
    }

    @Test
    void rejectsAnAnswerStoppedForAnyOtherReason() {
        responseBody = """
                {"candidates":[{"finishReason":"SAFETY","content":{"parts":[{"text":"{}"}]}}]}""";

        assertThatThrownBy(() -> gateway("test-key").complete(PROMPT))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("SAFETY");
    }

    @Test
    void sendsTheConfiguredTokenBudget() {
        responseBody = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{}"}]}}]}""";

        gateway("test-key", 4096).complete(PROMPT);

        assertThat(requestBodies).singleElement()
                .satisfies(body -> assertThat(body).contains("\"maxOutputTokens\":4096"));
    }

    @Test
    void failsWithoutCallingTheProviderWhenNoCredentialIsConfigured() {
        assertThatThrownBy(() -> gateway("").complete(PROMPT))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("GEMINI_API_KEY");

        assertThat(requestBodies)
                .as("an unconfigured gateway must fall back instantly, not open a socket")
                .isEmpty();
    }
}

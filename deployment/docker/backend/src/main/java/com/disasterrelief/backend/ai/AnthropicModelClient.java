package com.disasterrelief.backend.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the Anthropic Messages API. The key comes from ANTHROPIC_API_KEY and is never logged or returned. */
@Component
class AnthropicModelClient implements ModelClient {

    private final RestClient client;
    private final String apiKey;
    private final String model;

    AnthropicModelClient(@Value("${drrc.ai.api-key:}") String apiKey,
                         @Value("${drrc.ai.model:claude-sonnet-5-5}") String model,
                         @Value("${drrc.ai.base-url:https://api.anthropic.com}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public boolean configured() {
        return !apiKey.isEmpty();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String complete(String system, String userContent) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 800,
                    "system", system,
                    "messages", List.of(Map.of("role", "user", "content", userContent)));
            Map<String, Object> response = client.post().uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            Object content = response == null ? null : response.get("content");
            if (content instanceof List<?> blocks && !blocks.isEmpty() && blocks.get(0) instanceof Map<?, ?> first
                    && first.get("text") instanceof String text) {
                return text;
            }
            throw new ModelUnavailableException("The model returned no text.", null);
        } catch (ModelUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ModelUnavailableException("The model could not be reached.", ex);
        }
    }
}

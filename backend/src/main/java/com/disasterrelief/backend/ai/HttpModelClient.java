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

/** Calls the configured model API over HTTP. The key comes from AI_API_KEY and is never logged or returned. */
@Component
class HttpModelClient implements ModelClient {

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String versionHeader;
    private final String version;

    HttpModelClient(@Value("${drrc.ai.api-key:}") String apiKey,
                         @Value("${drrc.ai.model:}") String model,
                         @Value("${drrc.ai.base-url:}") String baseUrl,
                         @Value("${drrc.ai.version-header:}") String versionHeader,
                         @Value("${drrc.ai.version:}") String version) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.versionHeader = versionHeader == null ? "" : versionHeader.trim();
        this.version = version == null ? "" : version.trim();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl.trim());
        }
        this.client = builder.build();
    }

    @Override
    public boolean configured() {
        return !apiKey.isEmpty() && !model.isEmpty();
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
            RestClient.RequestBodySpec request = client.post().uri("/v1/messages").header("x-api-key", apiKey);
            if (!versionHeader.isEmpty() && !version.isEmpty()) {
                request = request.header(versionHeader, version);
            }
            Map<String, Object> response = request
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

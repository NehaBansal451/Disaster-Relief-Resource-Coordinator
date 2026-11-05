package com.disasterrelief.frontend.api;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls GET /api/system/status on the backend. Never throws: failures become an "unreachable" result. */
@Service
public class BackendStatusClient {

    private final RestClient restClient;
    private final String baseUrl;

    public BackendStatusClient(RestClient backendRestClient, @Value("${drrc.backend.url}") String baseUrl) {
        this.restClient = backendRestClient;
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public BackendStatus fetch() {
        try {
            Map<String, Object> body = restClient.get().uri("/api/system/status").retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (body == null) {
                return BackendStatus.unreachable();
            }
            Object db = body.get("database");
            Map<?, ?> database = db instanceof Map<?, ?> m ? m : Map.of();
            return new BackendStatus(true, text(body.get("status")), text(database.get("status")),
                    text(database.get("postgis")), text(database.get("latestMigration")));
        } catch (RestClientException ex) {
            return BackendStatus.unreachable();
        }
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}

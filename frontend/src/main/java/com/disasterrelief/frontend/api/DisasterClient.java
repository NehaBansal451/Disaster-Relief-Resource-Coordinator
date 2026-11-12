package com.disasterrelief.frontend.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Disaster endpoints. Every call sends the user's bearer token; the backend enforces the role rules. */
@Service
public class DisasterClient {

    private final RestClient restClient;

    public DisasterClient(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    /** @param status   ACTIVE, MONITORING, CLOSED or null for all
     *  @param severity CRITICAL, HIGH, MEDIUM, LOW or null for all */
    public PageDto<DisasterDto> list(String token, String status, String severity, int page, int size) {
        return ApiCalls.execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/disasters").queryParam("page", page).queryParam("size", size);
                    if (status != null) {
                        uriBuilder.queryParam("status", status);
                    }
                    if (severity != null) {
                        uriBuilder.queryParam("severity", severity);
                    }
                    return uriBuilder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<PageDto<DisasterDto>>() { }));
    }

    public DisasterDto get(String token, long id) {
        return ApiCalls.execute(() -> restClient.get().uri("/api/disasters/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(DisasterDto.class));
    }

    public DisasterDto create(String token, DisasterForm form) {
        return ApiCalls.execute(() -> restClient.post().uri("/api/disasters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(form)
                .retrieve()
                .body(DisasterDto.class));
    }
}

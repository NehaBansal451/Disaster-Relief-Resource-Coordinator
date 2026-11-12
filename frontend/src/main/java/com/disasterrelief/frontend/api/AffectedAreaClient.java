package com.disasterrelief.frontend.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AffectedAreaClient {

    private final RestClient restClient;

    public AffectedAreaClient(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    public PageDto<AffectedAreaDto> list(String token, long disasterId, int page, int size) {
        return ApiCalls.execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/affected-areas")
                        .queryParam("disasterId", disasterId)
                        .queryParam("page", page).queryParam("size", size).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<PageDto<AffectedAreaDto>>() { }));
    }

    public AffectedAreaDto create(String token, AffectedAreaForm form) {
        return ApiCalls.execute(() -> restClient.post().uri("/api/affected-areas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(form)
                .retrieve()
                .body(AffectedAreaDto.class));
    }
}

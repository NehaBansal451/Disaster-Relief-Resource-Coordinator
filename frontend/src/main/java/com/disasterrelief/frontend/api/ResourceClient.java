package com.disasterrelief.frontend.api;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Read-only resource catalogue, used by the request form. */
@Service
public class ResourceClient {

    private final RestClient restClient;

    public ResourceClient(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    public List<ResourceDto> list(String token) {
        return ApiCalls.execute(() -> restClient.get().uri("/api/resources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ResourceDto>>() { }));
    }
}

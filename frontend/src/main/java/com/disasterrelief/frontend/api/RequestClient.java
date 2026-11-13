package com.disasterrelief.frontend.api;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Need-request endpoints. Every call sends the user's bearer token; the backend enforces the role rules. */
@Service
public class RequestClient {

    private final RestClient restClient;

    public RequestClient(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    /** Any filter may be null. */
    public PageDto<NeedRequestDto> list(String token, String status, String severity, Long disasterId, int page, int size) {
        return ApiCalls.execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/requests").queryParam("page", page).queryParam("size", size);
                    if (status != null) {
                        uriBuilder.queryParam("status", status);
                    }
                    if (severity != null) {
                        uriBuilder.queryParam("severity", severity);
                    }
                    if (disasterId != null) {
                        uriBuilder.queryParam("disasterId", disasterId);
                    }
                    return uriBuilder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<PageDto<NeedRequestDto>>() { }));
    }

    public NeedRequestDto get(String token, long id) {
        return ApiCalls.execute(() -> restClient.get().uri("/api/requests/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().body(NeedRequestDto.class));
    }

    public NeedRequestDto submit(String token, NeedRequestForm form) {
        return ApiCalls.execute(() -> restClient.post().uri("/api/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(form)
                .retrieve().body(NeedRequestDto.class));
    }

    /** @param decision VERIFY, REJECT or REQUEST_CLARIFICATION */
    public NeedRequestDto verify(String token, long id, String decision, String reason) {
        return patch(token, id, "verify", Map.of("decision", decision, "reason", reason == null ? "" : reason));
    }

    public NeedRequestDto approve(String token, long id, String note) {
        return patch(token, id, "approve", Map.of("note", note == null ? "" : note));
    }

    public NeedRequestDto reject(String token, long id, String reason) {
        return patch(token, id, "reject", Map.of("reason", reason == null ? "" : reason));
    }

    public NeedRequestDto cancel(String token, long id, String reason) {
        return patch(token, id, "cancel", Map.of("reason", reason == null ? "" : reason));
    }

    private NeedRequestDto patch(String token, long id, String action, Map<String, String> body) {
        return ApiCalls.execute(() -> restClient.patch().uri("/api/requests/{id}/{action}", id, action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .retrieve().body(NeedRequestDto.class));
    }
}

package com.disasterrelief.frontend.api;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** POST /api/auth/login. Failures surface as {@link BackendException} (401 for bad credentials). */
@Service
public class AuthClient {

    private final RestClient restClient;

    public AuthClient(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    public LoginResult login(String email, String password) {
        return ApiCalls.execute(() -> restClient.post().uri("/api/auth/login")
                .body(Map.of("email", email == null ? "" : email, "password", password == null ? "" : password))
                .retrieve()
                .body(LoginResult.class));
    }
}

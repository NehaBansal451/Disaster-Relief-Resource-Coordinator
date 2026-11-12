package com.disasterrelief.frontend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthClientTest {

    private static final String BASE = "http://backend.test";

    @Test
    void loginParsesTokenAndUser() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/auth/login"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"email\":\"officer@demo.drrc.local\""),
                        org.hamcrest.Matchers.containsString("\"password\":\"pw\""))))
                .andRespond(withSuccess("{\"accessToken\":\"abc\",\"tokenType\":\"Bearer\",\"expiresIn\":3600,"
                        + "\"user\":{\"id\":2,\"fullName\":\"Demo Officer\",\"email\":\"officer@demo.drrc.local\","
                        + "\"role\":\"OFFICER\"}}", MediaType.APPLICATION_JSON));

        LoginResult result = new AuthClient(builder.build()).login("officer@demo.drrc.local", "pw");

        assertEquals("abc", result.accessToken());
        assertEquals(3600, result.expiresIn());
        assertTrue(result.user().canManageDisasters());
        assertEquals(false, result.user().isAdmin());
    }

    @Test
    void badCredentialsAreAn401BackendException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/auth/login")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"status\":401,\"detail\":\"Invalid email or password.\",\"code\":\"UNAUTHENTICATED\"}"));

        BackendException ex = assertThrows(BackendException.class,
                () -> new AuthClient(builder.build()).login("a@b.c", "wrong"));

        assertTrue(ex.isUnauthenticated());
        assertEquals("Invalid email or password.", ex.getMessage());
    }

    @Test
    void onlyAdminAndOfficerMayManageDisasters() {
        for (String role : new String[] {"WAREHOUSE_MANAGER", "FIELD_WORKER", "NGO", "VOLUNTEER", "CITIZEN"}) {
            assertEquals(false, new AuthUser(1L, "n", "e", role).canManageDisasters(), role);
        }
        assertTrue(new AuthUser(1L, "n", "e", "ADMIN").canManageDisasters());
        assertTrue(new AuthUser(1L, "n", "e", "OFFICER").canManageDisasters());
    }
}

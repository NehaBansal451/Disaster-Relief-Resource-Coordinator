package com.disasterrelief.frontend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BackendStatusClientTest {

    private static final String BASE = "http://backend.test";

    @Test
    void parsesBackendStatusResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/system/status"))
                .andExpect(header("Authorization", "Bearer test-token")).andRespond(withSuccess(
                "{\"application\":\"backend\",\"status\":\"UP\",\"database\":{\"status\":\"UP\","
                        + "\"postgis\":\"3.4\",\"latestMigration\":\"4\"}}", MediaType.APPLICATION_JSON));

        BackendStatus status = new BackendStatusClient(builder.build(), BASE).fetch("test-token");

        assertTrue(status.reachable());
        assertEquals("UP", status.databaseStatus());
        assertEquals("4", status.latestMigration());
        server.verify();
    }

    @Test
    void returnsUnreachableInsteadOfThrowingWhenBackendFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/system/status")).andRespond(withServerError());

        BackendStatus status = new BackendStatusClient(builder.build(), BASE).fetch("test-token");

        assertFalse(status.reachable());
        assertEquals("UNREACHABLE", status.status());
    }

    @Test
    void reportsForbiddenWhenTheBackendRefusesTheUser() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/system/status"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        BackendStatus status = new BackendStatusClient(builder.build(), BASE).fetch("test-token");

        assertTrue(status.reachable());
        assertEquals("FORBIDDEN", status.status());
    }
}

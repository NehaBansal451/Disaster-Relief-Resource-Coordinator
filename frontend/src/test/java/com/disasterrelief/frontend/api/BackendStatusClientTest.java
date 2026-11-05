package com.disasterrelief.frontend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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
        server.expect(requestTo(BASE + "/api/system/status")).andRespond(withSuccess(
                "{\"application\":\"backend\",\"status\":\"UP\",\"database\":{\"status\":\"UP\","
                        + "\"postgis\":\"3.4\",\"latestMigration\":\"4\"}}", MediaType.APPLICATION_JSON));

        BackendStatus status = new BackendStatusClient(builder.build(), BASE).fetch();

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

        BackendStatus status = new BackendStatusClient(builder.build(), BASE).fetch();

        assertFalse(status.reachable());
        assertEquals("UNREACHABLE", status.status());
    }
}

package com.disasterrelief.frontend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DisasterClientTest {

    private static final String BASE = "http://backend.test";
    private static final String DISASTER = "{\"id\":7,\"name\":\"Flood\",\"disasterType\":\"FLOOD\",\"severity\":\"HIGH\","
            + "\"status\":\"ACTIVE\",\"startedAt\":\"2026-11-10T08:00:00+05:30\",\"endedAt\":null,\"description\":null,"
            + "\"latitude\":30.28,\"longitude\":76.1,\"demo\":false,\"createdAt\":\"2026-11-10T08:01:00+05:30\"}";

    private RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private DisasterClient client = new DisasterClient(builder.build());

    @Test
    void listSendsFiltersPagingAndBearerToken() {
        server.expect(requestTo(BASE + "/api/disasters?page=0&size=20&status=ACTIVE"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"content\":[" + DISASTER + "],\"page\":0,\"size\":20,"
                        + "\"totalElements\":1,\"totalPages\":1}", MediaType.APPLICATION_JSON));

        PageDto<DisasterDto> page = client.list("tok", "ACTIVE", null, 0, 20);

        assertEquals(1, page.totalElements());
        assertEquals("Flood", page.content().get(0).name());
        assertEquals(30.28, page.content().get(0).latitude());
        server.verify();
    }

    @Test
    void createPostsTheFormAndReadsTheResult() {
        server.expect(requestTo(BASE + "/api/disasters"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"Flood\"")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(DISASTER));

        DisasterDto created = client.create("tok", new DisasterForm("Flood", "FLOOD", "HIGH",
                "2026-11-10T08:00:00+05:30", null, 30.28, 76.1));

        assertEquals(7L, created.id());
        server.verify();
    }

    @Test
    void validationErrorsBecomeFieldErrors() {
        server.expect(requestTo(BASE + "/api/disasters")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"status\":400,\"detail\":\"Request validation failed.\",\"code\":\"VALIDATION_ERROR\","
                        + "\"errors\":[{\"field\":\"endedAt\",\"message\":\"endedAt is required when status is CLOSED\"}]}"));

        BackendException ex = assertThrows(BackendException.class,
                () -> client.create("tok", new DisasterForm("x", "FLOOD", "LOW", "2026-11-10T08:00:00+05:30", null, null, null)));

        assertEquals(400, ex.status());
        assertEquals("VALIDATION_ERROR", ex.code());
        assertEquals("endedAt is required when status is CLOSED", ex.fieldErrors().get("endedAt"));
    }

    @Test
    void unauthorizedAndForbiddenAreRecognised() {
        server.expect(requestTo(BASE + "/api/disasters/7")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"status\":401,\"detail\":\"Authentication is required.\",\"code\":\"UNAUTHENTICATED\"}"));
        assertTrue(assertThrows(BackendException.class, () -> client.get("tok", 7)).isUnauthenticated());

        server.expect(requestTo(BASE + "/api/disasters/7")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertTrue(assertThrows(BackendException.class, () -> client.get("tok", 7)).isForbidden());
    }

    @Test
    void nonJsonErrorBodyStillGivesAUsableException() {
        server.expect(requestTo(BASE + "/api/disasters/7")).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_HTML).body("<html>proxy error</html>"));

        BackendException ex = assertThrows(BackendException.class, () -> client.get("tok", 7));

        assertEquals(502, ex.status());
        assertTrue(ex.fieldErrors().isEmpty());
    }

    @Test
    void connectionFailureIsReportedAsUnreachable() {
        RestClient unreachable = RestClient.builder().baseUrl("http://127.0.0.1:1").build();

        BackendException ex = assertThrows(BackendException.class,
                () -> new DisasterClient(unreachable).list("tok", null, null, 0, 20));

        assertTrue(ex.isUnreachable());
    }
}

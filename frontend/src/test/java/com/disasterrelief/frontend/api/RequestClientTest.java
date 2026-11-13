package com.disasterrelief.frontend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RequestClientTest {

    private static final String BASE = "http://backend.test";
    private static final String REQUEST = "{\"id\":9,\"disasterId\":1,\"disasterName\":\"Flood\",\"affectedAreaId\":2,"
            + "\"affectedAreaName\":\"Khera\",\"resourceId\":3,\"resourceName\":\"Water\",\"unit\":\"litre\","
            + "\"requestedBy\":4,\"requestedByName\":\"Field\",\"quantity\":500,\"affectedPopulation\":100,"
            + "\"severity\":\"HIGH\",\"description\":\"No water\",\"status\":\"PENDING\",\"statusReason\":null,"
            + "\"priorityScore\":null,\"priorityCategory\":null,\"priorityReasons\":[],\"approvedBy\":null,"
            + "\"approvedAt\":null,\"createdAt\":\"2026-11-10T08:01:00+05:30\",\"history\":[]}";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RequestClient client = new RequestClient(builder.build());

    @Test
    void listSendsFiltersAndToken() {
        server.expect(requestTo(BASE + "/api/requests?page=0&size=20&status=PENDING&disasterId=1"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"content\":[" + REQUEST + "],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}",
                        MediaType.APPLICATION_JSON));
        PageDto<NeedRequestDto> page = client.list("tok", "PENDING", null, 1L, 0, 20);
        assertEquals("Water", page.content().get(0).resourceName());
        assertEquals(500, page.content().get(0).quantity());
        server.verify();
    }

    @Test
    void submitPostsTheFormAndReadsPendingRequest() {
        server.expect(requestTo(BASE + "/api/requests")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("\"quantity\":500")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(REQUEST));
        NeedRequestDto created = client.submit("tok", new NeedRequestForm(1L, 2L, 3L, 500, 100, "HIGH", "No water"));
        assertEquals("PENDING", created.status());
        server.verify();
    }

    @Test
    void verifyApproveRejectAndCancelUseTheRightEndpoints() {
        for (String action : new String[] {"verify", "approve", "reject", "cancel"}) {
            server.expect(requestTo(BASE + "/api/requests/9/" + action)).andExpect(method(HttpMethod.PATCH))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andRespond(withSuccess(REQUEST, MediaType.APPLICATION_JSON));
        }
        client.verify("tok", 9, "VERIFY", null);
        client.approve("tok", 9, null);
        client.reject("tok", 9, "duplicate");
        client.cancel("tok", 9, "not needed");
        server.verify();
    }

    @Test
    void conflictIsTranslatedWithTheBackendMessage() {
        server.expect(requestTo(BASE + "/api/requests/9/approve"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":409,\"code\":\"INVALID_STATE\",\"detail\":\"Request 9 is PENDING and cannot be moved to APPROVED.\"}"));
        BackendException ex = assertThrows(BackendException.class, () -> client.approve("tok", 9, null));
        assertEquals(409, ex.status());
        assertEquals("INVALID_STATE", ex.code());
    }
}

package com.disasterrelief.backend.allocation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Day 6-7 acceptance test against the real database and demo data (password Demo@12345):
 * request -> verify (priority) -> approve -> recommend -> approve allocation (stock reserved) -> dispatch ->
 * in transit -> delivered, plus the concurrency rule "two officers cannot over-allocate the same stock".
 * Skipped when DB_PASSWORD is not set. Rows it creates are described "IT-..." (requests) or batch "IT-TRUCK"; safe to delete.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = "drrc.security.jwt-secret=integration-test-only-secret-0123456789abcdef")
class AllocationDispatchIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FIRST_ID = Pattern.compile("^\\{\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Demo@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(body);
        if (!m.find()) {
            throw new AssertionError("no accessToken");
        }
        return "Bearer " + m.group(1);
    }

    private String getBody(String url, String token) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long find(String body, String regex) {
        Matcher m = Pattern.compile(regex).matcher(body);
        if (!m.find()) {
            throw new AssertionError("pattern not found: " + regex);
        }
        return Long.parseLong(m.group(1));
    }

    private static long idOf(String body) {
        Matcher m = FIRST_ID.matcher(body);
        if (!m.find()) {
            throw new AssertionError("no id in: " + body);
        }
        return Long.parseLong(m.group(1));
    }

    private long resourceId(String name, String token) throws Exception {
        return find(getBody("/api/resources", token), "\"id\":(\\d+),\"name\":\"" + name + "\"");
    }

    private long warehouseId(String code, String token) throws Exception {
        return find(getBody("/api/warehouses", token), "\"id\":(\\d+),\"code\":\"" + code + "\"");
    }

    private long[] areaAndDisaster(String token) throws Exception {
        String body = getBody("/api/affected-areas?disasterId=1&size=1", token);
        return new long[] {find(body, "\"id\":(\\d+)"), find(body, "\"disasterId\":(\\d+)")};
    }

    private String json(MockMvc m, String method, String url, String token, String body, int expected) throws Exception {
        var builder = method.equals("PATCH") ? patch(url) : post(url);
        return m.perform(builder.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private long submitVerifyApprove(String field, String officer, long resource, int quantity, String text) throws Exception {
        long[] ad = areaAndDisaster(officer);
        String created = json(mvc, "POST", "/api/requests", field, "{\"disasterId\":" + ad[1] + ",\"affectedAreaId\":" + ad[0]
                + ",\"resourceId\":" + resource + ",\"quantity\":" + quantity
                + ",\"affectedPopulation\":900,\"severity\":\"CRITICAL\",\"description\":\"" + text + "\"}", 201);
        long id = idOf(created);
        json(mvc, "PATCH", "/api/requests/" + id + "/verify", officer, "{\"decision\":\"VERIFY\"}", 200);
        json(mvc, "PATCH", "/api/requests/" + id + "/approve", officer, "{}", 200);
        return id;
    }

    @Test
    void fullChainFromRequestToDeliveredWithPriorityStockAndAudit() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String field = login("field@demo.drrc.local");
        String wh1 = login("warehouse1@demo.drrc.local");
        String wh2 = login("warehouse2@demo.drrc.local");
        long blanket = resourceId("Blanket", officer);
        long ptl = warehouseId("PTL-01", officer);

        long[] ad = areaAndDisaster(officer);
        String created = json(mvc, "POST", "/api/requests", field, "{\"disasterId\":" + ad[1] + ",\"affectedAreaId\":" + ad[0]
                + ",\"resourceId\":" + blanket + ",\"quantity\":5,\"affectedPopulation\":900,\"severity\":\"CRITICAL\","
                + "\"description\":\"IT-request chain\"}", 201);
        long requestId = idOf(created);

        // verify calculates an explainable priority
        mvc.perform(patch("/api/requests/" + requestId + "/verify").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VERIFY\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"priorityScore\":null"))))
                .andExpect(content().string(containsString("Severity CRITICAL: 100 of 100 (weight 40%)")));
        // an override needs a reason
        json(mvc, "PATCH", "/api/requests/" + requestId + "/priority-override", officer, "{\"category\":\"LOW\",\"reason\":\" \"}", 400);
        json(mvc, "PATCH", "/api/requests/" + requestId + "/priority-override", officer,
                "{\"category\":\"HIGH\",\"reason\":\"IT override\"}", 200);
        json(mvc, "PATCH", "/api/requests/" + requestId + "/approve", officer, "{}", 200);

        // recommendation and approval reserve stock exactly once
        String rec = json(mvc, "POST", "/api/allocations/recommendations", officer, "{\"requestId\":" + requestId + "}", 201);
        assertTrue(rec.contains("\"allocatedQty\":5"), rec);
        assertTrue(rec.contains("\"unfilledQty\":0"), rec);
        long allocationId = idOf(rec);
        json(mvc, "POST", "/api/allocations/" + allocationId + "/approve", field, "{\"version\":1}", 403);
        String approved = json(mvc, "POST", "/api/allocations/" + allocationId + "/approve", officer, "{\"version\":1,\"items\":[{\"warehouseId\":" + ptl + ",\"quantity\":5}]}", 200);
        assertTrue(approved.contains("\"status\":\"APPROVED\""), approved);
        json(mvc, "POST", "/api/allocations/" + allocationId + "/approve", officer, "{\"version\":2}", 409);
        assertTrue(getBody("/api/requests/" + requestId, officer).contains("\"status\":\"ALLOCATED\""));

        // dispatch: wrong manager refused, right manager succeeds, no second dispatch
        String dispatchBody = "{\"allocationId\":" + allocationId + ",\"warehouseId\":" + ptl + ",\"vehicle\":\"PB-11-IT\",\"driverName\":\"IT driver\"}";
        json(mvc, "POST", "/api/dispatches", wh2, dispatchBody, 403);
        String dispatch = json(mvc, "POST", "/api/dispatches", wh1, dispatchBody, 201);
        assertTrue(dispatch.contains("\"status\":\"DISPATCHED\""), dispatch);
        long dispatchId = idOf(dispatch);
        json(mvc, "POST", "/api/dispatches", wh1, dispatchBody, 409);
        assertTrue(getBody("/api/requests/" + requestId, officer).contains("\"status\":\"DISPATCHED\""));

        // status steps: cannot skip IN_TRANSIT
        json(mvc, "PATCH", "/api/dispatches/" + dispatchId + "/status", wh1, "{\"status\":\"DELIVERED\"}", 409);
        json(mvc, "PATCH", "/api/dispatches/" + dispatchId + "/status", field, "{\"status\":\"IN_TRANSIT\"}", 200);
        assertTrue(getBody("/api/requests/" + requestId, officer).contains("\"status\":\"IN_TRANSIT\""));
        String delivered = json(mvc, "PATCH", "/api/dispatches/" + dispatchId + "/status", wh1, "{\"status\":\"DELIVERED\"}", 200);
        assertTrue(delivered.contains("\"status\":\"DELIVERED\""), delivered);
        String finalRequest = getBody("/api/requests/" + requestId, officer);
        assertTrue(finalRequest.contains("\"status\":\"DELIVERED\""), finalRequest);
        assertTrue(finalRequest.contains("\"action\":\"DELIVERED\""));

        // audit, alerts, map, dashboard reflect the real workflow
        String audit = getBody("/api/audit?entityType=need_requests&entityId=" + requestId + "&size=50", officer);
        assertTrue(audit.contains("REQUEST_DELIVERED") && audit.contains("REQUEST_APPROVED") && audit.contains("PRIORITY_OVERRIDDEN"), audit);
        mvc.perform(get("/api/audit").header(HttpHeaders.AUTHORIZATION, field)).andExpect(status().isForbidden());
        assertTrue(getBody("/api/alerts?size=50", officer).contains("CRITICAL_REQUEST"));
        getBody("/api/map", officer);
        assertTrue(getBody("/api/dashboard", officer).contains("\"DELIVERED\""));
        assertTrue(getBody("/api/inventory/" + find(getBody("/api/inventory?warehouseId=" + ptl + "&resourceId=" + blanket + "&size=1", officer),
                "\"id\":(\\d+)") + "/transactions", officer).contains("\"type\":\"DISPATCH\""));
    }

    @Test
    void twoOfficersCannotOverAllocateTheSameStock() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String field = login("field@demo.drrc.local");
        String wh1 = login("warehouse1@demo.drrc.local");
        long truck = resourceId("Light truck", officer);
        long ptl = warehouseId("PTL-01", officer);

        // earlier runs may have left open IT requests; they would count as competing demand and shrink both shares
        jdbc.update("UPDATE need_requests SET status = 'CANCELLED', status_reason = 'IT cleanup', updated_at = now() "
                + "WHERE description LIKE 'IT-request%' AND status IN ('VERIFIED','APPROVED')");
        json(mvc, "POST", "/api/inventory/receipts", wh1, "{\"warehouseId\":" + ptl + ",\"resourceId\":" + truck
                + ",\"quantity\":10,\"batchRef\":\"IT-TRUCK\"}", 201);
        String stock = getBody("/api/inventory?resourceId=" + truck + "&size=100", officer);
        int available = 0;
        Matcher m = Pattern.compile("\"available\":(\\d+)").matcher(stock);
        while (m.find()) {
            available += Integer.parseInt(m.group(1));
        }
        assertTrue(available >= 10, "expected at least 10 trucks available, got " + available);

        // both requests want ALL available trucks in every warehouse, so only one of them can ever be approved
        long a = submitVerifyApprove(field, officer, truck, available, "IT-request A");
        long allocationA = idOf(json(mvc, "POST", "/api/allocations/recommendations", officer, "{\"requestId\":" + a + "}", 201));
        long b = submitVerifyApprove(field, officer, truck, available, "IT-request B");
        String recB = json(mvc, "POST", "/api/allocations/recommendations", officer, "{\"requestId\":" + b + "}", 201);
        long allocationB = idOf(recB);
        assertTrue(recB.contains("Fairness policy"), "second recommendation should be capped by the fairness policy: " + recB);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<Integer>> tasks = List.of(
                () -> approveWhenReady(go, officer, allocationA),
                () -> approveWhenReady(go, officer, allocationB));
        List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
        go.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 200) {
                ok++;
            } else if (code == 409) {
                conflict++;
            }
        }
        pool.shutdown();
        assertEquals(1, ok, "exactly one approval may win");
        assertEquals(1, conflict, "the other must be refused with 409");

        String after = getBody("/api/inventory?resourceId=" + truck + "&size=100", officer);
        long onHand = 0;
        long reserved = 0;
        Matcher o = Pattern.compile("\"onHand\":(\\d+),\"reserved\":(\\d+)").matcher(after);
        while (o.find()) {
            onHand += Long.parseLong(o.group(1));
            reserved += Long.parseLong(o.group(2));
        }
        assertTrue(reserved <= onHand, "reserved " + reserved + " must never exceed on hand " + onHand);
    }

    private int approveWhenReady(CountDownLatch go, String officer, long allocationId) throws Exception {
        go.await();
        return mvc.perform(post("/api/allocations/" + allocationId + "/approve").header(HttpHeaders.AUTHORIZATION, officer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andReturn().getResponse().getStatus();
    }
}

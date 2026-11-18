package com.disasterrelief.backend.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
 * Day 8-9 acceptance test on the real database and demo users (password Demo@12345): volunteers and assignments,
 * partner offers, analytics, the PDF report, the login throttle and the report analyzer without an API key.
 * Skipped when DB_PASSWORD is not set. Rows it creates are described "IT-..." and are safe to delete.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = {"drrc.security.jwt-secret=integration-test-only-secret-0123456789abcdef", "drrc.ai.api-key="})
class Day8To10ApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FIRST_ID = Pattern.compile("^\\{\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

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

    private String send(String method, String url, String token, String body, int expected) throws Exception {
        var builder = method.equals("PATCH") ? patch(url) : post(url);
        return mvc.perform(builder.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String read(String url, String token, int expected) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    private static long idOf(String body) {
        Matcher m = FIRST_ID.matcher(body);
        if (!m.find()) {
            throw new AssertionError("no id in: " + body);
        }
        return Long.parseLong(m.group(1));
    }

    @Test
    void volunteerCanBeAssignedOnceAndFreedWhenTheTaskIsDone() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String field = login("field@demo.drrc.local");
        send("POST", "/api/volunteers", field, "{\"fullName\":\"IT-volunteer\"}", 403);
        long volunteer = idOf(send("POST", "/api/volunteers", officer,
                "{\"fullName\":\"IT-volunteer\",\"phone\":\"9800000000\",\"skills\":\"boat, first aid\",\"district\":\"Patiala\"}", 201));
        send("POST", "/api/volunteers", officer, "{\"fullName\":\" \"}", 400);

        String body = "{\"disasterId\":1,\"task\":\"IT-task: distribute blankets\"}";
        long assignment = idOf(send("POST", "/api/volunteers/" + volunteer + "/assignments", officer, body, 201));
        assertTrue(read("/api/volunteers/" + volunteer, officer, 200).contains("\"availability\":\"BUSY\""));
        send("POST", "/api/volunteers/" + volunteer + "/assignments", officer, body, 409);

        send("PATCH", "/api/assignments/" + assignment + "/status", officer, "{\"status\":\"COMPLETED\"}", 409);
        send("PATCH", "/api/assignments/" + assignment + "/status", officer, "{\"status\":\"ACCEPTED\"}", 200);
        send("PATCH", "/api/assignments/" + assignment + "/status", officer, "{\"status\":\"COMPLETED\"}", 200);
        assertTrue(read("/api/volunteers/" + volunteer, officer, 200).contains("\"availability\":\"AVAILABLE\""));
    }

    @Test
    void partnerOffersAreCreatedByNgoAndDecidedByOfficer() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String ngo = login("ngo@demo.drrc.local");
        String field = login("field@demo.drrc.local");
        String resources = read("/api/resources", officer, 200);
        Matcher m = Pattern.compile("\"id\":(\\d+),\"name\":\"Blanket\"").matcher(resources);
        if (!m.find()) {
            throw new AssertionError("Blanket resource missing");
        }
        String offer = "{\"resourceId\":" + m.group(1) + ",\"quantity\":50,\"description\":\"IT-offer\"}";
        send("POST", "/api/partner-contributions", field, offer, 403);
        send("POST", "/api/partner-contributions", ngo, "{\"resourceId\":" + m.group(1) + ",\"quantity\":0}", 400);
        long id = idOf(send("POST", "/api/partner-contributions", ngo, offer, 201));
        send("PATCH", "/api/partner-contributions/" + id + "/status", ngo, "{\"status\":\"ACCEPTED\"}", 403);
        send("PATCH", "/api/partner-contributions/" + id + "/status", officer, "{\"status\":\"RECEIVED\"}", 409);
        assertTrue(send("PATCH", "/api/partner-contributions/" + id + "/status", officer, "{\"status\":\"ACCEPTED\"}", 200)
                .contains("\"status\":\"ACCEPTED\""));
        assertTrue(read("/api/partner-contributions?size=100", officer, 200).contains("IT-offer"));
    }

    @Test
    void analyticsAndPdfReportAreForAdminAndOfficerOnly() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String field = login("field@demo.drrc.local");
        assertTrue(read("/api/analytics/summary", officer, 200).contains("\"totalRequests\""));
        assertTrue(read("/api/analytics/shortages", officer, 200).startsWith("["));
        read("/api/analytics/summary?disasterId=1", officer, 200);
        read("/api/analytics/summary", field, 403);
        read("/api/reports/situation.pdf", field, 403);

        var result = mvc.perform(get("/api/reports/situation.pdf?disasterId=1").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(String.valueOf(result.getContentType()).startsWith("application/pdf"));
        assertEquals("%PDF-", new String(result.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII));
        read("/api/reports/situation.pdf?disasterId=999999", officer, 404);
    }

    @Test
    void loginIsThrottledAfterRepeatedFailures() throws Exception {
        String email = "throttle-" + System.nanoTime() + "@demo.drrc.local";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"wrong-" + i + "\"}")).andExpect(status().isUnauthorized());
        }
        String blocked = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-6\"}"))
                .andExpect(status().isTooManyRequests()).andReturn().getResponse().getContentAsString();
        assertTrue(blocked.contains("TOO_MANY_ATTEMPTS"), blocked);
        login("officer@demo.drrc.local");
    }

    @Test
    void reportAnalyzerWithoutAnApiKeyFailsCleanlyAndNeverChangesRequests() throws Exception {
        String field = login("field@demo.drrc.local");
        String officer = login("officer@demo.drrc.local");
        String citizen = login("citizen@demo.drrc.local");
        String text = "{\"text\":\"IT-report: 40 families stranded near the river bridge, need drinking water and blankets. Call 9876543210.\"}";
        send("POST", "/api/ai/analyses", citizen, text, 403);
        String refused = send("POST", "/api/ai/analyses", field, text, 503);
        assertTrue(refused.contains("AI_UNAVAILABLE"), refused);
        send("POST", "/api/ai/analyses", field, "{\"text\":\"short\"}", 400);
        String list = read("/api/ai/analyses?size=5", officer, 200);
        assertTrue(list.contains("FAILED"), list);
        assertTrue(!list.contains("9876543210"), "phone numbers must never be stored");
        send("PATCH", "/api/ai/analyses/" + idOf(list.replace("{\"content\":[", "")) + "/review", officer, "{\"decision\":\"ACCEPTED\"}", 409);
    }
}

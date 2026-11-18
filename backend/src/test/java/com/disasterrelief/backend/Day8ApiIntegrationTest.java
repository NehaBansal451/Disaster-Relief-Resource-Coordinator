package com.disasterrelief.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Day 8 acceptance test against the real database and demo users (password Demo@12345): volunteers and assignments,
 * partner contributions, and the report analyzer WITHOUT a model key (it must fail safely with 503 and keep no
 * personal data). Skipped when DB_PASSWORD is not set. Rows it creates are named "IT-..." and are safe to delete.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = {"drrc.security.jwt-secret=integration-test-only-secret-0123456789abcdef", "drrc.ai.api-key="})
class Day8ApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_ID = Pattern.compile("\"user\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern FIRST_ID = Pattern.compile("^\\{\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    private record Login(String bearer, long userId) {
    }

    private Login login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Demo@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher t = TOKEN.matcher(body);
        Matcher u = USER_ID.matcher(body);
        if (!t.find() || !u.find()) {
            throw new AssertionError("login response not understood");
        }
        return new Login("Bearer " + t.group(1), Long.parseLong(u.group(1)));
    }

    private String send(String method, String url, Login who, String body, int expected) throws Exception {
        var builder = method.equals("PATCH") ? patch(url) : post(url);
        return mvc.perform(builder.header(HttpHeaders.AUTHORIZATION, who.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String read(String url, Login who, int expected) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, who.bearer())).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    private static long idOf(String body) {
        Matcher m = FIRST_ID.matcher(body);
        if (!m.find()) {
            throw new AssertionError("no id in: " + body);
        }
        return Long.parseLong(m.group(1));
    }

    private static long find(String body, String regex) {
        Matcher m = Pattern.compile(regex).matcher(body);
        if (!m.find()) {
            throw new AssertionError("pattern not found: " + regex + " in " + body);
        }
        return Long.parseLong(m.group(1));
    }

    @Test
    void volunteerRegistrationAssignmentAndSelfService() throws Exception {
        Login officer = login("officer@demo.drrc.local");
        Login field = login("field@demo.drrc.local");
        Login volunteer = login("volunteer@demo.drrc.local");

        send("POST", "/api/volunteers", field, "{\"fullName\":\"IT-denied\"}", 403);
        read("/api/volunteers", volunteer, 403);
        send("POST", "/api/volunteers", officer, "{\"fullName\":\" \"}", 400);

        // a volunteer without a login: cannot be assigned twice
        String created = send("POST", "/api/volunteers", officer,
                "{\"fullName\":\"IT-Volunteer " + System.nanoTime() + "\",\"phone\":\"0000000000\",\"district\":\"Patiala\"}", 201);
        long plainId = idOf(created);
        assertTrue(read("/api/volunteers?availability=AVAILABLE&size=100&district=Patiala", officer, 200).contains("\"id\":" + plainId));
        String assignment = send("POST", "/api/volunteers/" + plainId + "/assignments", officer,
                "{\"disasterId\":1,\"task\":\"IT-task\"}", 201);
        assertTrue(assignment.contains("\"status\":\"ASSIGNED\""), assignment);
        send("POST", "/api/volunteers/" + plainId + "/assignments", officer, "{\"disasterId\":1,\"task\":\"again\"}", 409);
        send("POST", "/api/volunteers/" + plainId + "/assignments", field, "{\"disasterId\":1,\"task\":\"x\"}", 403);

        // the demo volunteer user manages their own record
        send("POST", "/api/volunteers", officer, "{\"fullName\":\"IT-Self\",\"userId\":" + volunteer.userId() + "}", selfCreateStatus(officer, volunteer));
        String list = read("/api/volunteers?size=100&sort=createdAt", officer, 200);
        long selfId = find(list, "\"id\":(\\d+),\"userId\":" + volunteer.userId());
        send("PATCH", "/api/volunteers/" + selfId + "/availability", officer, "{\"availability\":\"AVAILABLE\"}", 200);
        long task = idOf(send("POST", "/api/volunteers/" + selfId + "/assignments", officer,
                "{\"disasterId\":1,\"task\":\"IT-self-task\"}", 201));
        send("PATCH", "/api/assignments/" + task + "/status", field, "{\"status\":\"ACCEPTED\"}", 403);
        send("PATCH", "/api/assignments/" + task + "/status", volunteer, "{\"status\":\"COMPLETED\"}", 409);
        send("PATCH", "/api/assignments/" + task + "/status", volunteer, "{\"status\":\"ACCEPTED\"}", 200);
        send("PATCH", "/api/assignments/" + task + "/status", volunteer, "{\"status\":\"COMPLETED\"}", 200);
        assertTrue(read("/api/volunteers/" + selfId, officer, 200).contains("\"availability\":\"AVAILABLE\""));
        assertTrue(read("/api/volunteers/" + selfId + "/assignments", volunteer, 200).contains("\"status\":\"COMPLETED\""));
        send("PATCH", "/api/volunteers/" + plainId + "/availability", volunteer, "{\"availability\":\"OFF_DUTY\"}", 403);
    }

    /** First run creates the record (201); later runs find it already there (409). Both are correct. */
    private int selfCreateStatus(Login officer, Login volunteer) throws Exception {
        String list = read("/api/volunteers?size=100&sort=createdAt", officer, 200);
        return Pattern.compile("\"userId\":" + volunteer.userId() + "[,}]").matcher(list).find() ? 409 : 201;
    }

    @Test
    void partnerContributionLifeCycleAndVisibility() throws Exception {
        Login officer = login("officer@demo.drrc.local");
        Login ngo = login("ngo@demo.drrc.local");
        Login field = login("field@demo.drrc.local");
        long resource = find(read("/api/resources", officer, 200), "\"id\":(\\d+),\"name\":\"Blanket\"");

        send("POST", "/api/partner-contributions", field, "{\"resourceId\":" + resource + ",\"quantity\":10}", 403);
        send("POST", "/api/partner-contributions", ngo, "{\"resourceId\":" + resource + ",\"quantity\":0}", 400);
        String offer = send("POST", "/api/partner-contributions", ngo,
                "{\"resourceId\":" + resource + ",\"quantity\":10,\"description\":\"IT-offer\"}", 201);
        long id = idOf(offer);
        assertTrue(offer.contains("\"status\":\"OFFERED\""), offer);

        send("PATCH", "/api/partner-contributions/" + id + "/status", ngo, "{\"status\":\"ACCEPTED\"}", 403);
        send("PATCH", "/api/partner-contributions/" + id + "/status", officer, "{\"status\":\"RECEIVED\"}", 409);
        send("PATCH", "/api/partner-contributions/" + id + "/status", officer, "{\"status\":\"ACCEPTED\"}", 200);
        send("PATCH", "/api/partner-contributions/" + id + "/status", officer, "{\"status\":\"DECLINED\"}", 409);
        String received = send("PATCH", "/api/partner-contributions/" + id + "/status", officer, "{\"status\":\"RECEIVED\"}", 200);
        assertTrue(received.contains("\"status\":\"RECEIVED\""), received);

        assertTrue(read("/api/partner-contributions?size=100", ngo, 200).contains("\"id\":" + id));
        read("/api/partner-contributions", field, 403);
    }

    @Test
    void analyzerWithoutAModelKeyFailsSafelyAndKeepsNoPersonalData() throws Exception {
        Login officer = login("officer@demo.drrc.local");
        Login field = login("field@demo.drrc.local");
        Login volunteer = login("volunteer@demo.drrc.local");

        send("POST", "/api/ai/analyses", volunteer, "{\"text\":\"Village flooded, 300 people need food and boats.\"}", 403);
        send("POST", "/api/ai/analyses", field, "{\"text\":\"too short\"}", 400);
        mvc.perform(post("/api/ai/analyses").header(HttpHeaders.AUTHORIZATION, field.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"IT-report: village flooded, 300 stranded. Call 98765 43210 or mail ravi@example.com.\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("AI_UNAVAILABLE")));

        String list = read("/api/ai/analyses?size=5", officer, 200);
        assertTrue(list.contains("AI_NOT_CONFIGURED"), list);
        assertTrue(list.contains("[PHONE]") && list.contains("[EMAIL]"), list);
        mvc.perform(get("/api/ai/analyses?size=5").header(HttpHeaders.AUTHORIZATION, officer.bearer()))
                .andExpect(content().string(not(containsString("98765"))))
                .andExpect(content().string(not(containsString("ravi@example.com"))));

        long failedId = find(list, "\"id\":(\\d+),\"status\":\"FAILED\"");
        send("PATCH", "/api/ai/analyses/" + failedId + "/review", officer, "{\"decision\":\"ACCEPTED\"}", 409);
        send("PATCH", "/api/ai/analyses/" + failedId + "/review", field, "{\"decision\":\"ACCEPTED\"}", 403);
        send("PATCH", "/api/ai/analyses/" + failedId + "/review", officer, "{\"decision\":\"MAYBE\"}", 400);
    }
}

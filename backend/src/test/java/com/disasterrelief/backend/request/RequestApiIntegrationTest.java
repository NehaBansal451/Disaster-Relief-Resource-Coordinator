package com.disasterrelief.backend.request;

import static org.hamcrest.Matchers.containsString;
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
 * Day 4 acceptance test: submit, verify, approve, reject, cancel against the real database and demo users
 * (password Demo@12345). Skipped when DB_PASSWORD is not set. Creates a few rows described "IT-request"; safe to delete.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = "drrc.security.jwt-secret=integration-test-only-secret-0123456789abcdef")
class RequestApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("^\\{\"id\"\\s*:\\s*(\\d+)");

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

    /** First id of a list endpoint response, e.g. the seeded flood. */
    private long firstId(String url, String token, String contentFilter) throws Exception {
        String body = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
        if (!m.find()) {
            throw new AssertionError("no rows at " + url + contentFilter);
        }
        return Long.parseLong(m.group(1));
    }

    private String submitJson(long disaster, long area, long resource, int quantity, String text) {
        return "{\"disasterId\":" + disaster + ",\"affectedAreaId\":" + area + ",\"resourceId\":" + resource
                + ",\"quantity\":" + quantity + ",\"affectedPopulation\":100,\"severity\":\"HIGH\",\"description\":\"" + text + "\"}";
    }

    private long submit(String token, String json) throws Exception {
        String body = mvc.perform(post("/api/requests").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andExpect(content().string(containsString("\"status\":\"PENDING\"")))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
        m.find();
        return Long.parseLong(m.group(1));
    }

    @Test
    void fullWorkflowAndRoleRules() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String field = login("field@demo.drrc.local");
        long areaId = firstId("/api/affected-areas?disasterId=1", officer, "");
        String areaBody = mvc.perform(get("/api/affected-areas").param("disasterId", "1")
                .header(HttpHeaders.AUTHORIZATION, officer)).andReturn().getResponse().getContentAsString();
        Matcher disaster = Pattern.compile("\"disasterId\"\\s*:\\s*(\\d+)").matcher(areaBody);
        long disasterId = disaster.find() ? Long.parseLong(disaster.group(1)) : 1;
        long resourceId = firstId("/api/resources", officer, "");

        // validation: quantity 0 and an unknown area
        mvc.perform(post("/api/requests").header(HttpHeaders.AUTHORIZATION, field).contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson(disasterId, areaId, resourceId, 0, "IT-request bad")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/requests").header(HttpHeaders.AUTHORIZATION, field).contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson(disasterId, 999999, resourceId, 5, "IT-request bad")))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("affectedAreaId")));

        // a field worker submits; the field worker cannot verify
        long id = submit(field, submitJson(disasterId, areaId, resourceId, 25, "IT-request workflow"));
        mvc.perform(patch("/api/requests/" + id + "/verify").header(HttpHeaders.AUTHORIZATION, field)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VERIFY\"}"))
                .andExpect(status().isForbidden());

        // approve before verification is refused, then verify, then approve
        mvc.perform(patch("/api/requests/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("INVALID_STATE")));
        mvc.perform(patch("/api/requests/" + id + "/verify").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VERIFY\"}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"status\":\"VERIFIED\"")));
        mvc.perform(patch("/api/requests/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"status\":\"APPROVED\"")));
        mvc.perform(get("/api/requests/" + id).header(HttpHeaders.AUTHORIZATION, field))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"action\":\"APPROVED\"")));

        // requester cannot cancel an approved request; the officer can, with a reason
        mvc.perform(patch("/api/requests/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, field)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed mind\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/requests/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/requests/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"stock needed elsewhere\"}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"status\":\"CANCELLED\"")));
    }

    @Test
    void rejectNeedsReasonAndListFiltersByStatus() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String ngo = login("ngo@demo.drrc.local");
        long areaId = firstId("/api/affected-areas?disasterId=1", officer, "");
        String areaBody = mvc.perform(get("/api/affected-areas").param("disasterId", "1")
                .header(HttpHeaders.AUTHORIZATION, officer)).andReturn().getResponse().getContentAsString();
        Matcher disaster = Pattern.compile("\"disasterId\"\\s*:\\s*(\\d+)").matcher(areaBody);
        long disasterId = disaster.find() ? Long.parseLong(disaster.group(1)) : 1;
        long resourceId = firstId("/api/resources", officer, "");
        long id = submit(ngo, submitJson(disasterId, areaId, resourceId, 5, "IT-request reject"));

        mvc.perform(patch("/api/requests/" + id + "/verify").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/requests/" + id + "/verify").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\",\"reason\":\"duplicate\"}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"status\":\"REJECTED\"")));
        mvc.perform(get("/api/requests?status=REJECTED&size=5").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"totalElements\"")));
        mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests?sort=password,desc").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isBadRequest());
    }
}

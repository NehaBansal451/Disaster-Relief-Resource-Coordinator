package com.disasterrelief.backend;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
 * Day 3 acceptance test: login, roles, and disaster/affected-area endpoints against the real database and the seeded
 * demo users (password Demo@12345). Needs PostgreSQL/PostGIS and DB_PASSWORD; skipped (not failed) otherwise.
 * It creates a few rows with unique names in the dev database; they are labelled "IT-" and safe to delete.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = "drrc.security.jwt-secret=integration-test-only-secret-0123456789abcdef")
class ApiSecurityIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    private String loginAs(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Demo@12345\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"tokenType\":\"Bearer\"")))
                .andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(body);
        if (!m.find()) {
            throw new AssertionError("no accessToken in login response");
        }
        return "Bearer " + m.group(1);
    }

    private static String disasterJson(String name) {
        return "{\"name\":\"" + name + "\",\"disasterType\":\"FLOOD\",\"severity\":\"HIGH\","
                + "\"startedAt\":\"2026-11-10T08:00:00+05:30\",\"latitude\":30.28,\"longitude\":76.10}";
    }

    @Test
    void protectedEndpointWithoutTokenIs401WithProblemJson() throws Exception {
        mvc.perform(get("/api/disasters"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().string(containsString("\"code\":\"UNAUTHENTICATED\"")));
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mvc.perform(get("/api/disasters").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String wrong = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"officer@demo.drrc.local\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@demo.drrc.local\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(wrong, unknown);
    }

    @Test
    void loginValidatesBody() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"code\":\"VALIDATION_ERROR\"")));
    }

    @Test
    void emailLookupIsCaseInsensitive() throws Exception {
        loginAs("OFFICER@Demo.DRRC.local");
    }

    @Test
    void meReturnsTheLoggedInUserWithoutPasswordHash() throws Exception {
        String auth = loginAs("officer@demo.drrc.local");
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"role\":\"OFFICER\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("password"))));
    }

    @Test
    void officerCanListSeededDisasters() throws Exception {
        String auth = loginAs("officer@demo.drrc.local");
        mvc.perform(get("/api/disasters").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ghaggar")))
                .andExpect(content().string(containsString("\"totalElements\"")));
    }

    @Test
    void citizenAndVolunteerAreForbiddenFromDisasters() throws Exception {
        for (String email : new String[] {"citizen@demo.drrc.local", "volunteer@demo.drrc.local"}) {
            mvc.perform(get("/api/disasters").header(HttpHeaders.AUTHORIZATION, loginAs(email)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(containsString("\"code\":\"FORBIDDEN\"")));
        }
    }

    @Test
    void fieldWorkerCanReadButCannotCreateDisasters() throws Exception {
        String auth = loginAs("field@demo.drrc.local");
        mvc.perform(get("/api/disasters").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
        mvc.perform(post("/api/disasters").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(disasterJson("IT-forbidden")))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemStatusIsAdminOnly() throws Exception {
        mvc.perform(get("/api/system/status").header(HttpHeaders.AUTHORIZATION, loginAs("officer@demo.drrc.local")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/system/status").header(HttpHeaders.AUTHORIZATION, loginAs("admin@demo.drrc.local")))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointStaysPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void invalidDisasterBodyReturnsFieldErrors() throws Exception {
        String auth = loginAs("officer@demo.drrc.local");
        mvc.perform(post("/api/disasters").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"errors\"")))
                .andExpect(content().string(containsString("VALIDATION_ERROR")));
    }

    @Test
    void closedWithoutEndDateIsA400NotADatabase500() throws Exception {
        String auth = loginAs("officer@demo.drrc.local");
        mvc.perform(post("/api/disasters").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT-closed\",\"disasterType\":\"FLOOD\",\"severity\":\"LOW\","
                                + "\"status\":\"CLOSED\",\"startedAt\":\"2026-11-10T08:00:00+05:30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("endedAt")));
    }

    @Test
    void badPagingAndSortAreRejected() throws Exception {
        String auth = loginAs("officer@demo.drrc.local");
        mvc.perform(get("/api/disasters?size=0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/disasters?sort=password").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/disasters?status=NOPE").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void officerCreatesDisasterThenAreaAndBothAreReadBack() throws Exception {
        String auth = loginAs("officer@demo.drrc.local");
        String name = "IT-flood-" + UUID.randomUUID();

        String created = mvc.perform(post("/api/disasters").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(disasterJson(name)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(content().string(containsString("\"status\":\"ACTIVE\"")))
                .andExpect(content().string(containsString("\"latitude\":30.28")))
                .andReturn().getResponse().getContentAsString();
        Matcher id = ID.matcher(created);
        if (!id.find()) {
            throw new AssertionError("no id in create response");
        }
        long disasterId = Long.parseLong(id.group(1));

        mvc.perform(get("/api/disasters/" + disasterId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(content().string(containsString(name)));

        String areaJson = "{\"disasterId\":" + disasterId + ",\"name\":\"IT Village\",\"district\":\"Patiala\","
                + "\"populationEstimate\":900,\"impactLevel\":\"HIGH\",\"latitude\":30.30,\"longitude\":76.12}";
        mvc.perform(post("/api/affected-areas").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(areaJson))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"areaType\":\"VILLAGE\"")));

        mvc.perform(post("/api/affected-areas").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(areaJson))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("DUPLICATE_AREA")));

        mvc.perform(get("/api/affected-areas?disasterId=" + disasterId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("IT Village")))
                .andExpect(content().string(containsString("\"totalElements\":1")));
    }

    @Test
    void areaForUnknownDisasterIs404() throws Exception {
        mvc.perform(post("/api/affected-areas").header(HttpHeaders.AUTHORIZATION, loginAs("officer@demo.drrc.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disasterId\":999999,\"name\":\"X\",\"populationEstimate\":1,"
                                + "\"impactLevel\":\"LOW\",\"latitude\":30.0,\"longitude\":76.0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotAddAreaToAClosedDisaster() throws Exception {
        String auth = loginAs("officer@demo.drrc.local");
        // The seeded "DEMO: Simulated Heatwave" is CLOSED. Find its id through the API.
        String list = mvc.perform(get("/api/disasters?status=CLOSED").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher id = ID.matcher(list);
        if (!id.find()) {
            throw new AssertionError("no closed disaster found (is the dev seed loaded?)");
        }
        mvc.perform(post("/api/affected-areas").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disasterId\":" + id.group(1) + ",\"name\":\"IT closed\",\"populationEstimate\":1,"
                                + "\"impactLevel\":\"LOW\",\"latitude\":30.0,\"longitude\":76.0}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("INVALID_STATE")));
    }
}

package com.disasterrelief.backend.dashboard;

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
 * Day 5 acceptance test against the real database and demo data (password Demo@12345): inventory read and receipt,
 * role rules, ledger, shelters and dashboard. Skipped when DB_PASSWORD is not set. Rows it adds use batch "IT-..." or
 * the name "IT-shelter"; they are safe to delete.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = "drrc.security.jwt-secret=integration-test-only-secret-0123456789abcdef")
class DashboardApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

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

    private long firstId(String url, String token) throws Exception {
        String body = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
        if (!m.find()) {
            throw new AssertionError("no rows at " + url);
        }
        return Long.parseLong(m.group(1));
    }

    /** Finds the water resource id (non-perishable resources avoid needing an expiry date). */
    private long nonPerishableResourceId(String token) throws Exception {
        String body = mvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, token)).andReturn()
                .getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":(\\d+),\"name\":\"[^\"]*\",\"category\":\"[^\"]*\",\"unit\":\"[^\"]*\",\"perishable\":false").matcher(body);
        if (!m.find()) {
            throw new AssertionError("no non-perishable resource");
        }
        return Long.parseLong(m.group(1));
    }

    @Test
    void inventoryIsRoleRestricted() throws Exception {
        mvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventory").header(HttpHeaders.AUTHORIZATION, login("field@demo.drrc.local")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory?size=5").header(HttpHeaders.AUTHORIZATION, login("officer@demo.drrc.local")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"available\"")));
        mvc.perform(get("/api/inventory?sort=password").header(HttpHeaders.AUTHORIZATION, login("officer@demo.drrc.local")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void warehouseManagerReceivesOnlyIntoOwnWarehouse() throws Exception {
        String officer = login("officer@demo.drrc.local");
        String manager = login("warehouse1@demo.drrc.local");
        long resource = nonPerishableResourceId(officer);
        String warehouses = mvc.perform(get("/api/warehouses").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher own = Pattern.compile("\"id\":(\\d+),\"code\":\"PTL-01\"").matcher(warehouses);
        Matcher other = Pattern.compile("\"id\":(\\d+),\"code\":\"RPR-01\"").matcher(warehouses);
        if (!own.find() || !other.find()) {
            throw new AssertionError("demo warehouses missing");
        }
        String ok = "{\"warehouseId\":" + own.group(1) + ",\"resourceId\":" + resource + ",\"quantity\":10,\"batchRef\":\"IT-1\"}";
        String denied = "{\"warehouseId\":" + other.group(1) + ",\"resourceId\":" + resource + ",\"quantity\":10,\"batchRef\":\"IT-1\"}";

        mvc.perform(post("/api/inventory/receipts").header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isCreated()).andExpect(content().string(containsString("\"batchRef\":\"IT-1\"")));
        mvc.perform(post("/api/inventory/receipts").header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(denied))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/receipts").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/receipts").header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":" + own.group(1) + ",\"resourceId\":" + resource + ",\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        long inventoryId = firstId("/api/inventory?warehouseId=" + own.group(1) + "&resourceId=" + resource + "&size=1", officer);
        mvc.perform(get("/api/inventory/" + inventoryId + "/transactions").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"type\":\"RECEIPT\"")));
    }

    @Test
    void shelterOvercrowdingOccupancyUpdateAndAlternatives() throws Exception {
        String officer = login("officer@demo.drrc.local");
        mvc.perform(get("/api/shelters?overcrowdedOnly=true").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"overcrowded\":true")));
        String body = mvc.perform(post("/api/shelters").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT-shelter\",\"capacity\":50,\"latitude\":30.33,\"longitude\":76.39}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher id = Pattern.compile("^\\{\"id\":(\\d+)").matcher(body);
        id.find();
        mvc.perform(patch("/api/shelters/" + id.group(1) + "/occupancy").header(HttpHeaders.AUTHORIZATION, login("field@demo.drrc.local"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"occupancy\":70}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"overcrowded\":true")));
        mvc.perform(patch("/api/shelters/" + id.group(1) + "/occupancy").header(HttpHeaders.AUTHORIZATION, officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"occupancy\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/shelters/" + id.group(1) + "/alternatives").header(HttpHeaders.AUTHORIZATION, officer))
                .andExpect(status().isOk()).andExpect(content().string(containsString("distanceKm")));
        mvc.perform(post("/api/shelters").header(HttpHeaders.AUTHORIZATION, login("field@demo.drrc.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"capacity\":5,\"latitude\":30,\"longitude\":76}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardHasRealFiguresForStaffOnly() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, login("citizen@demo.drrc.local")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, login("officer@demo.drrc.local")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"requestsByStatus\"")))
                .andExpect(content().string(containsString("\"PENDING\"")))
                .andExpect(content().string(containsString("\"stock\"")))
                .andExpect(content().string(containsString("\"overcrowdedShelters\"")))
                .andExpect(content().string(containsString("\"recentActivity\"")));
    }
}

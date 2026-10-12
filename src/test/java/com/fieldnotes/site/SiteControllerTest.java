package com.fieldnotes.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldnotes.auth.dto.AuthResponse;
import com.fieldnotes.auth.dto.RegisterRequest;
import com.fieldnotes.auth.repository.RefreshTokenRepository;
import com.fieldnotes.customer.dto.CreateCustomerRequest;
import com.fieldnotes.customer.dto.CustomerResponse;
import com.fieldnotes.customer.repository.CustomerRepository;
import com.fieldnotes.site.dto.CreateSiteRequest;
import com.fieldnotes.site.dto.PatchSiteRequest;
import com.fieldnotes.site.dto.SiteResponse;
import com.fieldnotes.site.dto.UpdateSiteRequest;
import com.fieldnotes.site.entity.Site;
import com.fieldnotes.site.repository.SiteRepository;
import com.fieldnotes.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fieldnotes_site_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes-length-required",
        "jwt.expiration-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SiteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String user1Token;
    private String user2Token;
    private UUID user1CustomerId;
    private UUID user2CustomerId;

    @BeforeEach
    void setUp() throws Exception {
        siteRepository.deleteAll();
        customerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        user1Token = registerAndGetToken("siteuser1@example.com", "Password123!", "Site", "One");
        user2Token = registerAndGetToken("siteuser2@example.com", "Password123!", "Site", "Two");

        user1CustomerId = createCustomer(user1Token, "Customer User 1").id();
        user2CustomerId = createCustomer(user2Token, "Customer User 2").id();
    }

    private String registerAndGetToken(String email, String password, String firstName, String lastName) throws Exception {
        RegisterRequest request = new RegisterRequest(email, password, firstName, lastName, null);
        MvcResult result = mockMvc.perform(post("/api/fieldnotesatl/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.accessToken();
    }

    private CustomerResponse createCustomer(String token, String name) throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(null, name, "Contact", null, null, null);
        MvcResult result = mockMvc.perform(post("/api/fieldnotesatl/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CustomerResponse.class);
    }

    @Test
    void createSiteShouldSucceedForOwnedCustomer() throws Exception {
        CreateSiteRequest request = new CreateSiteRequest(
                null,
                user1CustomerId,
                "North Plant",
                "100 Industrial Parkway",
                "Building A",
                "Springfield",
                "IL",
                "62701",
                "USA",
                new BigDecimal("39.781721"),
                new BigDecimal("-89.650148")
        );

        mockMvc.perform(post("/api/fieldnotesatl/sites")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("North Plant")))
                .andExpect(jsonPath("$.customerId", is(user1CustomerId.toString())))
                .andExpect(jsonPath("$.addressLine1", is("100 Industrial Parkway")))
                .andExpect(jsonPath("$.latitude", is(39.781721)))
                .andExpect(jsonPath("$.version", is(0)));
    }

    @Test
    void createSiteUnderOtherUsersCustomerShouldFailWithForbidden() throws Exception {
        CreateSiteRequest request = new CreateSiteRequest(
                null,
                user2CustomerId, // User 2's customer
                "Infiltrated Site",
                "404 Unauthorized Way",
                null,
                null, null, null, null, null, null
        );

        // User 1 attempts to add site to User 2's customer
        mockMvc.perform(post("/api/fieldnotesatl/sites")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userIsolation_userCannotAccessOtherUsersSites() throws Exception {
        SiteResponse site = createSiteDirectly(user1Token, user1CustomerId, "User 1 Facility");

        // User 2 lists sites -> should be empty
        mockMvc.perform(get("/api/fieldnotesatl/sites")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // User 2 gets site by ID -> 403 Forbidden
        mockMvc.perform(get("/api/fieldnotesatl/sites/" + site.id())
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());

        // User 2 deletes site -> 403 Forbidden
        mockMvc.perform(delete("/api/fieldnotesatl/sites/" + site.id())
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void filterByCustomerIdAndSearch() throws Exception {
        createSiteDirectly(user1Token, user1CustomerId, "Downtown Office");
        createSiteDirectly(user1Token, user1CustomerId, "Airport Warehouse");

        // Filter by customerId
        mockMvc.perform(get("/api/fieldnotesatl/sites?customerId=" + user1CustomerId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Search by keyword "Airport"
        mockMvc.perform(get("/api/fieldnotesatl/sites?search=Airport")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Airport Warehouse")));
    }

    @Test
    void updateAndPatchSiteIncrementsVersion() throws Exception {
        SiteResponse created = createSiteDirectly(user1Token, user1CustomerId, "Original Site");

        UpdateSiteRequest update = new UpdateSiteRequest(
                "Renamed Site", "New Address", "Suite 2", "City", "State", "00000", "US", null, null
        );

        mockMvc.perform(put("/api/fieldnotesatl/sites/" + created.id())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed Site")))
                .andExpect(jsonPath("$.version", is(1)));

        PatchSiteRequest patch = new PatchSiteRequest(
                null, null, "Suite 300", null, null, null, null, null, null
        );

        mockMvc.perform(patch("/api/fieldnotesatl/sites/" + created.id())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressLine2", is("Suite 300")))
                .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void softDeleteLeavesTombstoneAndReturns404() throws Exception {
        SiteResponse created = createSiteDirectly(user1Token, user1CustomerId, "Site to Delete");

        mockMvc.perform(delete("/api/fieldnotesatl/sites/" + created.id())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fieldnotesatl/sites/" + created.id())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());

        Site inDb = siteRepository.findById(created.id()).orElse(null);
        assertNotNull(inDb);
        assertNotNull(inDb.getDeletedAt());
        assertTrue(inDb.getVersion() > 0);
    }

    private SiteResponse createSiteDirectly(String token, UUID customerId, String name) throws Exception {
        CreateSiteRequest request = new CreateSiteRequest(
                null, customerId, name, "123 Main St", null, null, null, null, null, null, null
        );
        MvcResult res = mockMvc.perform(post("/api/fieldnotesatl/sites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), SiteResponse.class);
    }
}

package com.fieldnotes.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldnotes.auth.dto.AuthResponse;
import com.fieldnotes.auth.dto.RegisterRequest;
import com.fieldnotes.customer.dto.CreateCustomerRequest;
import com.fieldnotes.customer.dto.CustomerResponse;
import com.fieldnotes.customer.dto.PatchCustomerRequest;
import com.fieldnotes.customer.dto.UpdateCustomerRequest;
import com.fieldnotes.customer.entity.Customer;
import com.fieldnotes.customer.repository.CustomerRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fieldnotes_customer_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private com.fieldnotes.auth.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() throws Exception {
        customerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        user1Token = registerAndGetToken("user1@example.com", "Password123!", "User", "One");
        user2Token = registerAndGetToken("user2@example.com", "Password123!", "User", "Two");
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

    @Test
    void createCustomerWithServerGeneratedId() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                null,
                "Acme Corp",
                "Alice",
                "+111222333",
                "alice@acme.com",
                "Important client"
        );

        mockMvc.perform(post("/api/fieldnotesatl/customers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Acme Corp")))
                .andExpect(jsonPath("$.contactName", is("Alice")))
                .andExpect(jsonPath("$.contactEmail", is("alice@acme.com")))
                .andExpect(jsonPath("$.version", is(0)));
    }

    @Test
    void createCustomerWithClientProvidedUuid() throws Exception {
        UUID clientId = UUID.randomUUID();
        CreateCustomerRequest request = new CreateCustomerRequest(
                clientId,
                "Offline Created Client",
                "Bob",
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/fieldnotesatl/customers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(clientId.toString())))
                .andExpect(jsonPath("$.name", is("Offline Created Client")))
                .andExpect(jsonPath("$.version", is(0)));
    }

    @Test
    void userIsolation_userCannotAccessOtherUsersCustomers() throws Exception {
        // User 1 creates customer
        CreateCustomerRequest request = new CreateCustomerRequest(
                null,
                "User 1 Private Customer",
                "Contact 1",
                null,
                null,
                null
        );

        MvcResult createResult = mockMvc.perform(post("/api/fieldnotesatl/customers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CustomerResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), CustomerResponse.class);
        UUID customerId = created.id();

        // User 2 lists customers -> should be empty
        mockMvc.perform(get("/api/fieldnotesatl/customers")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // User 2 tries to get customer by id -> 403 Forbidden
        mockMvc.perform(get("/api/fieldnotesatl/customers/" + customerId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());

        // User 2 tries to update customer -> 403 Forbidden
        UpdateCustomerRequest updateReq = new UpdateCustomerRequest(
                "Hacked Name", "Hacker", null, null, null
        );
        mockMvc.perform(put("/api/fieldnotesatl/customers/" + customerId)
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // User 2 tries to delete customer -> 403 Forbidden
        mockMvc.perform(delete("/api/fieldnotesatl/customers/" + customerId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchAndPaginationWorks() throws Exception {
        createCustomerDirectly(user1Token, "Alpha Logistics", "Maya");
        createCustomerDirectly(user1Token, "Beta Builders", "John");
        createCustomerDirectly(user1Token, "Gamma Technologies", "Alice");

        // Search for "Alpha"
        mockMvc.perform(get("/api/fieldnotesatl/customers?search=Alpha")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Alpha Logistics")));

        // Search for contact name "Alice"
        mockMvc.perform(get("/api/fieldnotesatl/customers?search=Alice")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Gamma Technologies")));
    }

    @Test
    void updateAndPatchCustomerIncrementsVersion() throws Exception {
        CustomerResponse created = createCustomerDirectly(user1Token, "Initial Name", "Contact");

        UpdateCustomerRequest updateRequest = new UpdateCustomerRequest(
                "Updated Name", "New Contact", "+99999", "new@example.com", "Updated notes"
        );

        mockMvc.perform(put("/api/fieldnotesatl/customers/" + created.id())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.version", is(1)));

        PatchCustomerRequest patchRequest = new PatchCustomerRequest(
                null, null, null, null, "Patched notes only"
        );

        mockMvc.perform(patch("/api/fieldnotesatl/customers/" + created.id())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.notes", is("Patched notes only")))
                .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void softDeleteLeavesTombstoneAndHidesFromApi() throws Exception {
        CustomerResponse created = createCustomerDirectly(user1Token, "To Delete", "Contact");

        // Delete customer
        mockMvc.perform(delete("/api/fieldnotesatl/customers/" + created.id())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        // GET by ID should now return 404
        mockMvc.perform(get("/api/fieldnotesatl/customers/" + created.id())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());

        // In database, the record still exists with deleted_at set and version incremented (tombstone)
        Customer inDb = customerRepository.findById(created.id()).orElse(null);
        assertNotNull(inDb);
        assertNotNull(inDb.getDeletedAt());
        assertTrue(inDb.getVersion() > 0);
    }

    private CustomerResponse createCustomerDirectly(String token, String name, String contactName) throws Exception {
        CreateCustomerRequest req = new CreateCustomerRequest(null, name, contactName, null, null, null);
        MvcResult res = mockMvc.perform(post("/api/fieldnotesatl/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), CustomerResponse.class);
    }
}

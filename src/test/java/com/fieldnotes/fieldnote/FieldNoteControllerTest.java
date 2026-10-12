package com.fieldnotes.fieldnote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldnotes.auth.dto.AuthResponse;
import com.fieldnotes.auth.dto.RegisterRequest;
import com.fieldnotes.auth.repository.RefreshTokenRepository;
import com.fieldnotes.customer.dto.CreateCustomerRequest;
import com.fieldnotes.customer.dto.CustomerResponse;
import com.fieldnotes.customer.repository.CustomerRepository;
import com.fieldnotes.fieldnote.dto.CreateFieldNoteRequest;
import com.fieldnotes.fieldnote.dto.FieldNoteResponse;
import com.fieldnotes.fieldnote.dto.PatchFieldNoteRequest;
import com.fieldnotes.fieldnote.dto.UpdateFieldNoteRequest;
import com.fieldnotes.fieldnote.entity.FieldNote;
import com.fieldnotes.fieldnote.entity.NoteStatus;
import com.fieldnotes.fieldnote.repository.FieldNoteRepository;
import com.fieldnotes.site.dto.CreateSiteRequest;
import com.fieldnotes.site.dto.SiteResponse;
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

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fieldnotes_note_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
class FieldNoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FieldNoteRepository fieldNoteRepository;

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
    private UUID user1SiteId;
    private UUID user2SiteId;
    private UUID user1CustomerId;

    @BeforeEach
    void setUp() throws Exception {
        fieldNoteRepository.deleteAll();
        siteRepository.deleteAll();
        customerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        user1Token = registerAndGetToken("noteuser1@example.com", "Password123!", "Note", "One");
        user2Token = registerAndGetToken("noteuser2@example.com", "Password123!", "Note", "Two");

        user1CustomerId = createCustomer(user1Token, "Customer 1").id();
        UUID user2CustomerId = createCustomer(user2Token, "Customer 2").id();

        user1SiteId = createSite(user1Token, user1CustomerId, "Site 1").id();
        user2SiteId = createSite(user2Token, user2CustomerId, "Site 2").id();
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

    private SiteResponse createSite(String token, UUID customerId, String name) throws Exception {
        CreateSiteRequest request = new CreateSiteRequest(
                null, customerId, name, "123 Road", null, null, null, null, null, null, null
        );
        MvcResult result = mockMvc.perform(post("/api/fieldnotesatl/sites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SiteResponse.class);
    }

    @Test
    void createFieldNoteShouldSucceed() throws Exception {
        CreateFieldNoteRequest request = new CreateFieldNoteRequest(
                null,
                user1SiteId,
                "Electrical Inspection",
                "Inspected main breaker panel. All within specs.",
                Instant.now(),
                NoteStatus.COMPLETED,
                null,
                null,
                "photos/site1-breaker.jpg"
        );

        mockMvc.perform(post("/api/fieldnotesatl/field-notes")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Electrical Inspection")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.photoReference", is("photos/site1-breaker.jpg")))
                .andExpect(jsonPath("$.version", is(0)));
    }

    @Test
    void createFieldNoteUnderOtherUsersSiteShouldFailWithForbidden() throws Exception {
        CreateFieldNoteRequest request = new CreateFieldNoteRequest(
                null,
                user2SiteId, // User 2's site
                "Unauthorized note",
                "Should not succeed",
                null, null, null, null, null
        );

        mockMvc.perform(post("/api/fieldnotesatl/field-notes")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userIsolation_userCannotAccessOtherUsersNotes() throws Exception {
        FieldNoteResponse note = createNoteDirectly(user1Token, user1SiteId, "User 1 Note", NoteStatus.PENDING);

        // User 2 lists notes -> should be empty
        mockMvc.perform(get("/api/fieldnotesatl/field-notes")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // User 2 gets note by id -> 403 Forbidden
        mockMvc.perform(get("/api/fieldnotesatl/field-notes/" + note.id())
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());

        // User 2 deletes note -> 403 Forbidden
        mockMvc.perform(delete("/api/fieldnotesatl/field-notes/" + note.id())
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void filterBySiteCustomerStatusAndSearch() throws Exception {
        createNoteDirectly(user1Token, user1SiteId, "Foundation Check", NoteStatus.PENDING);
        createNoteDirectly(user1Token, user1SiteId, "HVAC Service", NoteStatus.IN_PROGRESS);
        createNoteDirectly(user1Token, user1SiteId, "Final Signoff", NoteStatus.COMPLETED);

        // Filter by status = PENDING
        mockMvc.perform(get("/api/fieldnotesatl/field-notes?status=PENDING")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Foundation Check")));

        // Filter by customerId
        mockMvc.perform(get("/api/fieldnotesatl/field-notes?customerId=" + user1CustomerId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));

        // Search by keyword "HVAC"
        mockMvc.perform(get("/api/fieldnotesatl/field-notes?search=HVAC")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("HVAC Service")));
    }

    @Test
    void updateAndPatchNoteIncrementsVersion() throws Exception {
        FieldNoteResponse note = createNoteDirectly(user1Token, user1SiteId, "Initial Title", NoteStatus.PENDING);

        UpdateFieldNoteRequest update = new UpdateFieldNoteRequest(
                "Updated Title",
                "Updated Description",
                Instant.now(),
                NoteStatus.IN_PROGRESS,
                null, null, null
        );

        mockMvc.perform(put("/api/fieldnotesatl/field-notes/" + note.id())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                .andExpect(jsonPath("$.version", is(1)));

        PatchFieldNoteRequest patch = new PatchFieldNoteRequest(
                null, null, null, NoteStatus.COMPLETED, null, null, null
        );

        mockMvc.perform(patch("/api/fieldnotesatl/field-notes/" + note.id())
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void softDeleteLeavesTombstoneAndReturns404() throws Exception {
        FieldNoteResponse note = createNoteDirectly(user1Token, user1SiteId, "Delete Note", NoteStatus.PENDING);

        mockMvc.perform(delete("/api/fieldnotesatl/field-notes/" + note.id())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fieldnotesatl/field-notes/" + note.id())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());

        FieldNote inDb = fieldNoteRepository.findById(note.id()).orElse(null);
        assertNotNull(inDb);
        assertNotNull(inDb.getDeletedAt());
        assertTrue(inDb.getVersion() > 0);
    }

    private FieldNoteResponse createNoteDirectly(String token, UUID siteId, String title, NoteStatus status) throws Exception {
        CreateFieldNoteRequest req = new CreateFieldNoteRequest(
                null, siteId, title, "Sample description", null, status, null, null, null
        );
        MvcResult res = mockMvc.perform(post("/api/fieldnotesatl/field-notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), FieldNoteResponse.class);
    }
}

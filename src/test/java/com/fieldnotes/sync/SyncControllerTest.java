package com.fieldnotes.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldnotes.auth.dto.AuthResponse;
import com.fieldnotes.auth.dto.RegisterRequest;
import com.fieldnotes.auth.repository.RefreshTokenRepository;
import com.fieldnotes.customer.entity.Customer;
import com.fieldnotes.customer.repository.CustomerRepository;
import com.fieldnotes.fieldnote.entity.NoteStatus;
import com.fieldnotes.fieldnote.repository.FieldNoteRepository;
import com.fieldnotes.site.repository.SiteRepository;
import com.fieldnotes.sync.dto.*;
import com.fieldnotes.sync.repository.DeviceSyncCursorRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fieldnotes_sync_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
class SyncControllerTest {

    private static final String SYNC_URL = "/api/fieldnotesatl/sync";
    private static final String AUTH_URL = "/api/fieldnotesatl/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceSyncCursorRepository cursorRepository;

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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() throws Exception {
        cursorRepository.deleteAll();
        fieldNoteRepository.deleteAll();
        siteRepository.deleteAll();
        customerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        user1Token = registerUser("tech1@example.com", "Password123!");
        user2Token = registerUser("tech2@example.com", "Password123!");
    }

    private String registerUser(String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest(email, password, "Field", "Worker", "+14045550100");
        MvcResult result = mockMvc.perform(post(AUTH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.accessToken();
    }

    @Test
    void unauthenticatedSync_isRejected() throws Exception {
        SyncRequest request = new SyncRequest("device-001", null, null);
        mockMvc.perform(post(SYNC_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedSync_initialPull_returnsEmptyWhenNoData() throws Exception {
        SyncRequest request = new SyncRequest("device-001", null, null);
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCursor").isNotEmpty())
                .andExpect(jsonPath("$.customers", hasSize(0)))
                .andExpect(jsonPath("$.sites", hasSize(0)))
                .andExpect(jsonPath("$.fieldNotes", hasSize(0)))
                .andExpect(jsonPath("$.conflicts", hasSize(0)));
    }

    @Test
    void authenticatedSync_pushCreatesCustomerSiteAndFieldNote_preservesRelationships() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        CustomerSyncItem customerItem = new CustomerSyncItem(
                customerId, "Acme Corp", "Alice Smith", "+14045550111", "alice@acme.com", "Important client", 0);
        SiteSyncItem siteItem = new SiteSyncItem(
                siteId, customerId, "Headquarters", "100 Peachtree St", "Suite 400",
                "Atlanta", "GA", "30303", "USA",
                new BigDecimal("33.753746"), new BigDecimal("-84.386330"), 0);
        FieldNoteSyncItem noteItem = new FieldNoteSyncItem(
                noteId, siteId, "Initial Inspection", "Found minor pipe corrosion on floor 2",
                Instant.now(), NoteStatus.IN_PROGRESS,
                new BigDecimal("33.753746"), new BigDecimal("-84.386330"), "photos/pipe1.jpg", 0);

        SyncChanges changes = new SyncChanges(
                List.of(customerItem),
                List.of(siteItem),
                List.of(noteItem),
                null, null, null
        );
        SyncRequest request = new SyncRequest("device-001", null, changes);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCursor").isNotEmpty())
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.customers[0].id").value(customerId.toString()))
                .andExpect(jsonPath("$.customers[0].name").value("Acme Corp"))
                .andExpect(jsonPath("$.sites", hasSize(1)))
                .andExpect(jsonPath("$.sites[0].id").value(siteId.toString()))
                .andExpect(jsonPath("$.sites[0].customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.fieldNotes", hasSize(1)))
                .andExpect(jsonPath("$.fieldNotes[0].id").value(noteId.toString()))
                .andExpect(jsonPath("$.fieldNotes[0].siteId").value(siteId.toString()))
                .andExpect(jsonPath("$.fieldNotes[0].customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.conflicts", hasSize(0)));

        // Verify entities in database
        assertEquals(1, customerRepository.count());
        assertEquals(1, siteRepository.count());
        assertEquals(1, fieldNoteRepository.count());
        assertEquals(1, cursorRepository.count());
    }

    @Test
    void authenticatedSync_idempotency_repeatingSamePushDoesNotCreateDuplicates() throws Exception {
        UUID customerId = UUID.randomUUID();
        CustomerSyncItem customerItem = new CustomerSyncItem(
                customerId, "Beta LLC", "Bob Jones", null, "bob@beta.com", null, 0);
        SyncChanges changes = new SyncChanges(List.of(customerItem), null, null, null, null, null);
        SyncRequest request = new SyncRequest("device-001", null, changes);

        // First push
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertEquals(1, customerRepository.count());

        // Second identical push (client retry after timeout)
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts", hasSize(0)))
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.customers[0].id").value(customerId.toString()))
                .andExpect(jsonPath("$.customers[0].name").value("Beta LLC"));

        assertEquals(1, customerRepository.count());
    }

    @Test
    void authenticatedSync_crossUserIsolation_cannotAccessAnotherUserRecords() throws Exception {
        UUID customerId = UUID.randomUUID();
        CustomerSyncItem customerItem = new CustomerSyncItem(
                customerId, "User1 Private Client", "Owner", null, null, null, 0);
        SyncRequest pushReq = new SyncRequest("device-001", null,
                new SyncChanges(List.of(customerItem), null, null, null, null, null));

        // User 1 creates customer
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pushReq)))
                .andExpect(status().isOk());

        // User 2 pulls — should see 0 customers
        SyncRequest pullReq = new SyncRequest("device-002", null, null);
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pullReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(0)));

        // User 2 tries to push a site under User 1's customer — must fail with 401 or 403
        UUID siteId = UUID.randomUUID();
        SiteSyncItem unauthorizedSite = new SiteSyncItem(
                siteId, customerId, "Hacked Site", "123 St", null, null, null, null, null, null, null, 0);
        SyncRequest maliciousReq = new SyncRequest("device-002", null,
                new SyncChanges(null, List.of(unauthorizedSite), null, null, null, null));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedSync_invalidParentReference_isRejected() throws Exception {
        UUID nonexistentCustomerId = UUID.randomUUID();
        SiteSyncItem orphanedSite = new SiteSyncItem(
                UUID.randomUUID(), nonexistentCustomerId, "Orphan Site", "123 St", null, null, null, null, null, null, null, 0);
        SyncRequest request = new SyncRequest("device-001", null,
                new SyncChanges(null, List.of(orphanedSite), null, null, null, null));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedSync_versionConflict_detectedWhenServerIsAhead() throws Exception {
        UUID customerId = UUID.randomUUID();
        CustomerSyncItem initialItem = new CustomerSyncItem(
                customerId, "Gamma Corp", "Charlie", null, null, null, 0);

        // Step 1: Create record
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", null,
                                        new SyncChanges(List.of(initialItem), null, null, null, null, null)))))
                .andExpect(status().isOk());

        // Step 2: Simulate server being ahead (e.g., version incremented on another device)
        Customer customer = customerRepository.findById(customerId).orElseThrow();
        customer.setVersion(5);
        customer.setName("Gamma Corp Updated on Web");
        customerRepository.save(customer);

        // Step 3: Device sends update with stale version 0
        CustomerSyncItem staleItem = new CustomerSyncItem(
                customerId, "Gamma Corp Stale Mobile Edit", "Charlie", null, null, null, 0);
        SyncRequest stalePush = new SyncRequest("device-001", null,
                new SyncChanges(List.of(staleItem), null, null, null, null, null));

        MvcResult result = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stalePush)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].entityType").value("customer"))
                .andExpect(jsonPath("$.conflicts[0].entityId").value(customerId.toString()))
                .andExpect(jsonPath("$.conflicts[0].reason", containsString("Server version (5) is ahead")))
                .andReturn();

        // Server record remains unchanged
        Customer serverCustomer = customerRepository.findById(customerId).orElseThrow();
        assertEquals("Gamma Corp Updated on Web", serverCustomer.getName());
    }

    @Test
    void authenticatedSync_softDelete_returnsTombstoneInPull() throws Exception {
        UUID customerId = UUID.randomUUID();
        CustomerSyncItem initialItem = new CustomerSyncItem(
                customerId, "Delta Inc", "Dana", null, null, null, 0);

        // Step 1: Create record
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", null,
                                        new SyncChanges(List.of(initialItem), null, null, null, null, null)))))
                .andExpect(status().isOk());

        // Step 2: Delete record via sync
        SyncRequest deleteReq = new SyncRequest("device-001", null,
                new SyncChanges(null, null, null, List.of(customerId), null, null));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCustomerIds", hasSize(1)))
                .andExpect(jsonPath("$.deletedCustomerIds[0]").value(customerId.toString()));

        // Verify soft-deleted in DB
        Customer deletedCustomer = customerRepository.findById(customerId).orElseThrow();
        assertNotNull(deletedCustomer.getDeletedAt());
    }

    @Test
    void authenticatedSync_incrementalPull_returnsOnlyDeltasSinceCursor() throws Exception {
        UUID c1 = UUID.randomUUID();
        CustomerSyncItem item1 = new CustomerSyncItem(c1, "Customer 1", null, null, null, null, 0);

        // Batch 1: Create customer 1
        MvcResult res1 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", null,
                                        new SyncChanges(List.of(item1), null, null, null, null, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andReturn();

        SyncResponse sync1 = objectMapper.readValue(res1.getResponse().getContentAsString(), SyncResponse.class);
        Instant cursor1 = sync1.newCursor();
        assertNotNull(cursor1);

        // Age customer 1 beyond the 2-second overlap window so batch 2 will not include it
        jdbcTemplate.update("UPDATE customers SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(cursor1.minusSeconds(5)), c1);

        // Batch 2: Create customer 2
        UUID c2 = UUID.randomUUID();
        CustomerSyncItem item2 = new CustomerSyncItem(c2, "Customer 2", null, null, null, null, 0);

        MvcResult res2 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", cursor1,
                                        new SyncChanges(List.of(item2), null, null, null, null, null)))))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse sync2 = objectMapper.readValue(res2.getResponse().getContentAsString(), SyncResponse.class);
        // Only customer 2 is returned because customer 1 was updated before (cursor1 - 2s overlap window)
        assertEquals(1, sync2.customers().size());
        assertEquals(c2, sync2.customers().get(0).id());
    }

    @Test
    void authenticatedSync_recordCommittedDuringWindow_deliveredOnNextSyncWithOverlap() throws Exception {
        // Sync 1: initial empty sync establishing cursor1
        SyncRequest initReq = new SyncRequest("device-race-1", null, null);
        MvcResult res1 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse sync1 = objectMapper.readValue(res1.getResponse().getContentAsString(), SyncResponse.class);
        Instant cursor1 = sync1.newCursor();

        // Simulate a record committed concurrently right before cursor1 (e.g. 500ms before cursor1),
        // which was missed or committed concurrently during sync 1's boundary.
        UUID cRaceId = UUID.randomUUID();
        Customer raceCustomer = new Customer();
        raceCustomer.setId(cRaceId);
        raceCustomer.setUser(userRepository.findByEmail("tech1@example.com").orElseThrow());
        raceCustomer.setName("Concurrent Race Customer");
        customerRepository.save(raceCustomer);

        // Explicitly set updated_at to 500ms before cursor1 (inside the 2-second overlap window)
        jdbcTemplate.update("UPDATE customers SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(cursor1.minusMillis(500)), cRaceId);

        // Sync 2: Client syncs with lastSyncAt = cursor1
        SyncRequest sync2Req = new SyncRequest("device-race-1", cursor1, null);
        MvcResult res2 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sync2Req)))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse sync2 = objectMapper.readValue(res2.getResponse().getContentAsString(), SyncResponse.class);
        // The overlap window ensures raceCustomer is delivered on this sync rather than being skipped
        assertTrue(sync2.customers().stream().anyMatch(c -> c.id().equals(cRaceId)),
                "Record committed within the overlap window before cursor1 must be delivered on next sync");
    }

    @Test
    void authenticatedSync_customerDelete_cascadesChildTombstonesWithBumpedVersions() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        // 1. Create hierarchy: Customer -> Site -> FieldNote
        CustomerSyncItem customerItem = new CustomerSyncItem(
                customerId, "Cascade Customer", null, null, null, null, 0);
        SiteSyncItem siteItem = new SiteSyncItem(
                siteId, customerId, "Cascade Site", "Main St", null, null, null, null, null, null, null, 0);
        FieldNoteSyncItem noteItem = new FieldNoteSyncItem(
                noteId, siteId, "Cascade Note", "Testing cascade delete",
                Instant.now(), NoteStatus.PENDING, null, null, null, 0);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", null,
                                        new SyncChanges(List.of(customerItem), List.of(siteItem), List.of(noteItem), null, null, null)))))
                .andExpect(status().isOk());

        // 2. Soft-delete Customer via Sync
        SyncRequest deleteReq = new SyncRequest("device-001", null,
                new SyncChanges(null, null, null, List.of(customerId), null, null));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCustomerIds", hasSize(1)))
                .andExpect(jsonPath("$.deletedCustomerIds[0]").value(customerId.toString()))
                .andExpect(jsonPath("$.deletedSiteIds", hasSize(1)))
                .andExpect(jsonPath("$.deletedSiteIds[0]").value(siteId.toString()))
                .andExpect(jsonPath("$.deletedFieldNoteIds", hasSize(1)))
                .andExpect(jsonPath("$.deletedFieldNoteIds[0]").value(noteId.toString()));

        // Verify entities in database are marked deleted with bumped versions
        Customer c = customerRepository.findById(customerId).orElseThrow();
        assertNotNull(c.getDeletedAt());
        assertTrue(c.getVersion() >= 1);

        com.fieldnotes.site.entity.Site s = siteRepository.findById(siteId).orElseThrow();
        assertNotNull(s.getDeletedAt());
        assertTrue(s.getVersion() >= 1);

        com.fieldnotes.fieldnote.entity.FieldNote fn = fieldNoteRepository.findById(noteId).orElseThrow();
        assertNotNull(fn.getDeletedAt());
        assertTrue(fn.getVersion() >= 1);
    }

    @Test
    void authenticatedSync_pushFieldNoteWithDeletedParent_returnsConflict() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        // 1. Create Customer and Site
        CustomerSyncItem customerItem = new CustomerSyncItem(customerId, "Cust X", null, null, null, null, 0);
        SiteSyncItem siteItem = new SiteSyncItem(siteId, customerId, "Site X", "123 St", null, null, null, null, null, null, null, 0);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", null,
                                        new SyncChanges(List.of(customerItem), List.of(siteItem), null, null, null, null)))))
                .andExpect(status().isOk());

        // 2. Soft-delete Site
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", null,
                                        new SyncChanges(null, null, null, null, List.of(siteId), null)))))
                .andExpect(status().isOk());

        // 3. Client attempts to push a new FieldNote under the deleted Site
        FieldNoteSyncItem noteItem = new FieldNoteSyncItem(
                noteId, siteId, "Orphan Note", "Should be rejected",
                Instant.now(), NoteStatus.PENDING, null, null, null, 0);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-001", null,
                                        new SyncChanges(null, null, List.of(noteItem), null, null, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].entityType").value("fieldNote"))
                .andExpect(jsonPath("$.conflicts[0].entityId").value(noteId.toString()))
                .andExpect(jsonPath("$.conflicts[0].reason", containsString("Parent site is soft-deleted")));

        // Note is NOT persisted
        assertFalse(fieldNoteRepository.existsById(noteId));
    }

    @Test
    void authenticatedSync_offlineDeviceLongAbsence_receivesTombstones() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        // Device A creates Customer, Site, and Note
        CustomerSyncItem customerItem = new CustomerSyncItem(customerId, "Old Client", null, null, null, null, 0);
        SiteSyncItem siteItem = new SiteSyncItem(siteId, customerId, "Old Site", "Old Road", null, null, null, null, null, null, null, 0);
        FieldNoteSyncItem noteItem = new FieldNoteSyncItem(
                noteId, siteId, "Old Note", "Old inspection",
                Instant.now(), NoteStatus.COMPLETED, null, null, null, 0);

        MvcResult resA = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-A", null,
                                        new SyncChanges(List.of(customerItem), List.of(siteItem), List.of(noteItem), null, null, null)))))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse responseA = objectMapper.readValue(resA.getResponse().getContentAsString(), SyncResponse.class);
        Instant t0 = responseA.newCursor();

        // Device B (field tech who goes offline for a month) does initial sync at t0
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SyncRequest("device-B", t0, null))))
                .andExpect(status().isOk());

        // Meanwhile, back at office, Device A deletes customer
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-A", null,
                                        new SyncChanges(null, null, null, List.of(customerId), null, null)))))
                .andExpect(status().isOk());

        // Device B returns from offline absence and syncs with lastSyncAt = t0
        MvcResult resB = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SyncRequest("device-B", t0, null))))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse responseB = objectMapper.readValue(resB.getResponse().getContentAsString(), SyncResponse.class);
        assertTrue(responseB.deletedCustomerIds().contains(customerId), "Must receive customer tombstone");
        assertTrue(responseB.deletedSiteIds().contains(siteId), "Must receive cascaded site tombstone");
        assertTrue(responseB.deletedFieldNoteIds().contains(noteId), "Must receive cascaded field note tombstone");
    }

    @Test
    void authenticatedSync_clientSuppliedEarlierLastSyncAt_honoredForRepull() throws Exception {
        UUID c1 = UUID.randomUUID();
        CustomerSyncItem item1 = new CustomerSyncItem(c1, "Customer One", null, null, null, null, 0);

        // 1. Device syncs creating Customer 1
        MvcResult res1 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-prec-1", null,
                                        new SyncChanges(List.of(item1), null, null, null, null, null)))))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse sync1 = objectMapper.readValue(res1.getResponse().getContentAsString(), SyncResponse.class);
        Instant t1 = sync1.newCursor();

        // Age customer 1 so normal incremental sync would exclude it
        jdbcTemplate.update("UPDATE customers SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(t1.minusSeconds(10)), c1);

        // 2. Normal sync advancing stored cursor to t2
        MvcResult res2 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SyncRequest("device-prec-1", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(0)))
                .andReturn();

        SyncResponse sync2 = objectMapper.readValue(res2.getResponse().getContentAsString(), SyncResponse.class);
        Instant t2 = sync2.newCursor();
        assertTrue(t2.isAfter(t1) || t2.equals(t1));

        // 3. Client explicitly asks for a re-pull by sending lastSyncAt = EPOCH (earlier than stored cursor t2)
        MvcResult res3 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SyncRequest("device-prec-1", Instant.EPOCH, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.customers[0].id").value(c1.toString()))
                .andReturn();
    }

    @Test
    void authenticatedSync_clientSuppliedLaterLastSyncAt_ignoredInFavorOfStoredCursorToPreventSkippingData() throws Exception {
        UUID c1 = UUID.randomUUID();
        CustomerSyncItem item1 = new CustomerSyncItem(c1, "Customer Alpha", null, null, null, null, 0);

        // 1. Initial sync establishes stored cursor at t1
        MvcResult res1 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SyncRequest("device-prec-2", null,
                                        new SyncChanges(List.of(item1), null, null, null, null, null)))))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse sync1 = objectMapper.readValue(res1.getResponse().getContentAsString(), SyncResponse.class);
        Instant t1 = sync1.newCursor();

        // 2. Create customer 2 in background directly in database with updated_at = t1 + 50ms
        UUID c2 = UUID.randomUUID();
        Customer customer2 = new Customer();
        customer2.setId(c2);
        customer2.setUser(userRepository.findByEmail("tech1@example.com").orElseThrow());
        customer2.setName("Customer Beta (Pending Pull)");
        customerRepository.save(customer2);

        // 3. Client erroneously or maliciously sends a future timestamp lastSyncAt = t1 + 10 hours
        Instant futureTimestamp = t1.plus(java.time.Duration.ofHours(10));
        MvcResult res2 = mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SyncRequest("device-prec-2", futureTimestamp, null))))
                .andExpect(status().isOk())
                .andReturn();

        SyncResponse sync2 = objectMapper.readValue(res2.getResponse().getContentAsString(), SyncResponse.class);
        // The server-stored cursor t1 is authoritative! The client's future timestamp is ignored,
        // so customer 2 is NOT skipped and is safely returned to the client.
        assertTrue(sync2.customers().stream().anyMatch(c -> c.id().equals(c2)),
                "Server-stored cursor must prevent client from skipping data via a future timestamp");
    }
}

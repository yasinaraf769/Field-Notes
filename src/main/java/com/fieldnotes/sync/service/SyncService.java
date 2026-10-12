package com.fieldnotes.sync.service;

import com.fieldnotes.common.exception.ResourceNotFoundException;
import com.fieldnotes.common.exception.UnauthorizedOperationException;
import com.fieldnotes.customer.dto.CustomerResponse;
import com.fieldnotes.customer.entity.Customer;
import com.fieldnotes.customer.repository.CustomerRepository;
import com.fieldnotes.fieldnote.dto.FieldNoteResponse;
import com.fieldnotes.fieldnote.entity.FieldNote;
import com.fieldnotes.fieldnote.entity.NoteStatus;
import com.fieldnotes.fieldnote.repository.FieldNoteRepository;
import com.fieldnotes.site.dto.SiteResponse;
import com.fieldnotes.site.entity.Site;
import com.fieldnotes.site.repository.SiteRepository;
import com.fieldnotes.sync.dto.*;
import com.fieldnotes.sync.entity.DeviceSyncCursor;
import com.fieldnotes.sync.repository.DeviceSyncCursorRepository;
import com.fieldnotes.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Core business logic for the offline-first synchronisation endpoint.
 *
 * <h2>Algorithm (push then pull)</h2>
 * <ol>
 *   <li>Apply each pushed customer/site/field-note upsert in dependency order
 *       (customers → sites → field notes).</li>
 *   <li>Apply pushed soft-deletes.</li>
 *   <li>Determine the new server cursor using the database clock before pull queries run.</li>
 *   <li>Query all records changed since (previousCursor - overlapWindow) and return them.</li>
 *   <li>Persist the new cursor for this (user, device) pair.</li>
 * </ol>
 */
@Service
public class SyncService {

    public static final Duration OVERLAP_WINDOW = Duration.ofSeconds(2);

    private final CustomerRepository customerRepository;
    private final SiteRepository siteRepository;
    private final FieldNoteRepository fieldNoteRepository;
    private final DeviceSyncCursorRepository cursorRepository;
    private final JdbcTemplate jdbcTemplate;

    public SyncService(CustomerRepository customerRepository,
                       SiteRepository siteRepository,
                       FieldNoteRepository fieldNoteRepository,
                       DeviceSyncCursorRepository cursorRepository,
                       JdbcTemplate jdbcTemplate) {
        this.customerRepository = customerRepository;
        this.siteRepository = siteRepository;
        this.fieldNoteRepository = fieldNoteRepository;
        this.cursorRepository = cursorRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SyncResponse sync(User user, SyncRequest request) {
        Instant previousCursor = resolvePreviousCursor(user, request);
        List<SyncConflict> conflicts = new ArrayList<>();

        if (request.changes() != null) {
            applyCustomerUpserts(user, request.changes().customers(), conflicts);
            applySiteUpserts(user, request.changes().sites(), conflicts);
            applyFieldNoteUpserts(user, request.changes().fieldNotes(), conflicts);
            applyCustomerDeletes(user, request.changes().deletedCustomerIds());
            applySiteDeletes(user, request.changes().deletedSiteIds());
            applyFieldNoteDeletes(user, request.changes().deletedFieldNoteIds());
        }

        // Capture the new cursor using the database clock BEFORE pull queries run.
        Instant newCursor = getDatabaseCurrentTimestamp();

        // Calculate pull window with a 2-second overlap before previousCursor
        // to prevent missed updates caused by concurrent transaction commits or clock skew.
        Instant pullSince = calculatePullSince(previousCursor);

        // --- Pull: return everything changed since pullSince --------
        List<CustomerResponse> customers = pullCustomers(user, pullSince);
        List<SiteResponse> sites = pullSites(user, pullSince);
        List<FieldNoteResponse> fieldNotes = pullFieldNotes(user, pullSince);

        List<UUID> deletedCustomerIds = pullDeletedCustomerIds(user, pullSince);
        List<UUID> deletedSiteIds = pullDeletedSiteIds(user, pullSince);
        List<UUID> deletedFieldNoteIds = pullDeletedFieldNoteIds(user, pullSince);

        // Persist the new cursor
        persistCursor(user, request.deviceId(), newCursor);

        return SyncResponse.of(
                newCursor,
                customers,
                sites,
                fieldNotes,
                deletedCustomerIds,
                deletedSiteIds,
                deletedFieldNoteIds,
                conflicts
        );
    }

    private Instant getDatabaseCurrentTimestamp() {
        Timestamp ts = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        return ts != null ? ts.toInstant() : Instant.now();
    }

    private Instant calculatePullSince(Instant cursor) {
        if (cursor == null || cursor.equals(Instant.EPOCH)) {
            return Instant.EPOCH;
        }
        return cursor.isAfter(Instant.EPOCH.plus(OVERLAP_WINDOW))
                ? cursor.minus(OVERLAP_WINDOW)
                : Instant.EPOCH;
    }

    // =========================================================================
    // Cursor helpers
    // =========================================================================

    /**
     * Resolves the previous watermark cursor for a synchronization request.
     *
     * <h3>Precedence Rule</h3>
     * The server-stored {@link DeviceSyncCursor} is authoritative. A client-supplied
     * {@code lastSyncAt} is ONLY honored if it is EARLIER than the stored cursor
     * (allowing the client to request an intentional re-pull from an earlier point in time).
     * If the client-supplied {@code lastSyncAt} is equal to, later than the stored cursor,
     * or omitted (null), the server-stored cursor is used to prevent the client from
     * skipping un-pulled records.
     * If no cursor has yet been stored for this (user, device) pair, the client-supplied
     * {@code lastSyncAt} is used (or {@code Instant.EPOCH} if omitted).
     */
    private Instant resolvePreviousCursor(User user, SyncRequest request) {
        Optional<Instant> storedCursor = cursorRepository
                .findByUserIdAndDeviceId(user.getId(), request.deviceId())
                .map(DeviceSyncCursor::getLastSyncAt);

        if (storedCursor.isPresent()) {
            Instant stored = storedCursor.get();
            if (request.lastSyncAt() != null && request.lastSyncAt().isBefore(stored)) {
                // Client explicitly requested a re-pull from an earlier watermark
                return request.lastSyncAt();
            }
            // Stored cursor is authoritative; cannot skip data with later/future timestamp
            return stored;
        }

        // Initial sync for this device: use client-supplied cursor or EPOCH
        return request.lastSyncAt() != null ? request.lastSyncAt() : Instant.EPOCH;
    }

    private void persistCursor(User user, String deviceId, Instant newCursor) {
        DeviceSyncCursor cursor = cursorRepository
                .findByUserIdAndDeviceId(user.getId(), deviceId)
                .orElseGet(() -> {
                    DeviceSyncCursor c = new DeviceSyncCursor();
                    c.setUser(user);
                    c.setDeviceId(deviceId);
                    return c;
                });
        cursor.setLastSyncAt(newCursor);
        cursorRepository.save(cursor);
    }

    // =========================================================================
    // Push helpers — customers
    // =========================================================================

    private void applyCustomerUpserts(User user, List<CustomerSyncItem> items, List<SyncConflict> conflicts) {
        if (items == null || items.isEmpty()) return;

        for (CustomerSyncItem item : items) {
            Optional<Customer> existing = customerRepository.findById(item.id());

            if (existing.isEmpty()) {
                // Create
                Customer c = new Customer();
                c.setId(item.id());
                c.setUser(user);
                applyCustomerFields(c, item);
                c.setVersion(0);
                customerRepository.save(c);
            } else {
                Customer c = existing.get();
                // Ownership guard
                if (!c.getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot sync customer owned by another user: " + item.id());
                }
                if (c.getDeletedAt() != null) {
                    // Server-side soft-delete wins; surface as conflict so client can react
                    conflicts.add(new SyncConflict("customer", item.id(),
                            "Record is soft-deleted on the server"));
                    continue;
                }
                if (isCustomerContentEqual(c, item)) {
                    // Already applied (e.g. timed-out retry) — idempotent no-op, no conflict.
                    continue;
                }
                if (c.getVersion() > item.version()) {
                    // Server ahead and content differs — conflict
                    conflicts.add(new SyncConflict("customer", item.id(),
                            "Server version (" + c.getVersion() + ") is ahead of client version (" + item.version() + ")"));
                    continue;
                }
                // Apply (last-write-wins when versions match or server is behind)
                applyCustomerFields(c, item);
                c.setVersion(Math.max(c.getVersion(), item.version()) + 1);
                customerRepository.save(c);
            }
        }
    }

    private boolean isCustomerContentEqual(Customer c, CustomerSyncItem item) {
        return Objects.equals(c.getName(), item.name().trim())
                && Objects.equals(c.getContactName(), item.contactName() == null ? null : item.contactName().trim())
                && Objects.equals(c.getContactPhone(), item.contactPhone() == null ? null : item.contactPhone().trim())
                && Objects.equals(c.getContactEmail(), item.contactEmail() == null ? null : item.contactEmail().trim().toLowerCase())
                && Objects.equals(c.getNotes(), item.notes());
    }

    private void applyCustomerFields(Customer c, CustomerSyncItem item) {
        c.setName(item.name().trim());
        c.setContactName(item.contactName() == null ? null : item.contactName().trim());
        c.setContactPhone(item.contactPhone() == null ? null : item.contactPhone().trim());
        c.setContactEmail(item.contactEmail() == null ? null : item.contactEmail().trim().toLowerCase());
        c.setNotes(item.notes());
    }

    private void applyCustomerDeletes(User user, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return;
        Instant now = Instant.now();
        for (UUID id : ids) {
            customerRepository.findById(id).ifPresent(c -> {
                if (!c.getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot delete customer owned by another user: " + id);
                }
                if (c.getDeletedAt() == null) {
                    c.setDeletedAt(now);
                    c.setVersion(c.getVersion() + 1);
                    customerRepository.save(c);

                    cascadeSoftDeleteCustomerChildren(id, now);
                }
            });
        }
    }

    private void cascadeSoftDeleteCustomerChildren(UUID customerId, Instant now) {
        List<Site> sites = siteRepository.findAllByCustomerIdAndDeletedAtIsNull(customerId);
        for (Site site : sites) {
            site.setDeletedAt(now);
            site.setVersion(site.getVersion() + 1);
            siteRepository.save(site);

            List<FieldNote> notes = fieldNoteRepository.findAllBySiteIdAndDeletedAtIsNull(site.getId());
            for (FieldNote note : notes) {
                note.setDeletedAt(now);
                note.setVersion(note.getVersion() + 1);
                fieldNoteRepository.save(note);
            }
        }
    }

    // =========================================================================
    // Push helpers — sites
    // =========================================================================

    private void applySiteUpserts(User user, List<SiteSyncItem> items, List<SyncConflict> conflicts) {
        if (items == null || items.isEmpty()) return;

        for (SiteSyncItem item : items) {
            Optional<Site> existing = siteRepository.findById(item.id());

            if (existing.isEmpty()) {
                Customer customer = customerRepository.findById(item.customerId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Customer not found for site sync: " + item.customerId()));
                if (!customer.getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot sync site under customer owned by another user: " + item.customerId());
                }
                if (customer.getDeletedAt() != null) {
                    conflicts.add(new SyncConflict("site", item.id(),
                            "Parent customer is soft-deleted: " + item.customerId()));
                    continue;
                }
                Site s = new Site();
                s.setId(item.id());
                s.setCustomer(customer);
                applySiteFields(s, item);
                s.setVersion(0);
                siteRepository.save(s);
            } else {
                Site s = existing.get();
                if (!s.getCustomer().getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot sync site owned by another user: " + item.id());
                }
                if (s.getDeletedAt() != null) {
                    conflicts.add(new SyncConflict("site", item.id(),
                            "Record is soft-deleted on the server"));
                    continue;
                }
                if (s.getCustomer().getDeletedAt() != null) {
                    conflicts.add(new SyncConflict("site", item.id(),
                            "Parent customer is soft-deleted: " + s.getCustomer().getId()));
                    continue;
                }
                if (isSiteContentEqual(s, item)) {
                    // Already applied — idempotent no-op
                    continue;
                }
                if (s.getVersion() > item.version()) {
                    conflicts.add(new SyncConflict("site", item.id(),
                            "Server version (" + s.getVersion() + ") is ahead of client version (" + item.version() + ")"));
                    continue;
                }
                applySiteFields(s, item);
                s.setVersion(Math.max(s.getVersion(), item.version()) + 1);
                siteRepository.save(s);
            }
        }
    }

    private boolean isSiteContentEqual(Site s, SiteSyncItem item) {
        return Objects.equals(s.getName(), item.name().trim())
                && Objects.equals(s.getAddressLine1(), item.addressLine1().trim())
                && Objects.equals(s.getAddressLine2(), item.addressLine2() == null ? null : item.addressLine2().trim())
                && Objects.equals(s.getCity(), item.city() == null ? null : item.city().trim())
                && Objects.equals(s.getStateProvince(), item.stateProvince() == null ? null : item.stateProvince().trim())
                && Objects.equals(s.getPostalCode(), item.postalCode() == null ? null : item.postalCode().trim())
                && Objects.equals(s.getCountry(), item.country() == null ? null : item.country().trim())
                && ((s.getLatitude() == null && item.latitude() == null) || (s.getLatitude() != null && item.latitude() != null && s.getLatitude().compareTo(item.latitude()) == 0))
                && ((s.getLongitude() == null && item.longitude() == null) || (s.getLongitude() != null && item.longitude() != null && s.getLongitude().compareTo(item.longitude()) == 0));
    }

    private void applySiteFields(Site s, SiteSyncItem item) {
        s.setName(item.name().trim());
        s.setAddressLine1(item.addressLine1().trim());
        s.setAddressLine2(item.addressLine2() == null ? null : item.addressLine2().trim());
        s.setCity(item.city() == null ? null : item.city().trim());
        s.setStateProvince(item.stateProvince() == null ? null : item.stateProvince().trim());
        s.setPostalCode(item.postalCode() == null ? null : item.postalCode().trim());
        s.setCountry(item.country() == null ? null : item.country().trim());
        s.setLatitude(item.latitude());
        s.setLongitude(item.longitude());
    }

    private void applySiteDeletes(User user, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return;
        Instant now = Instant.now();
        for (UUID id : ids) {
            siteRepository.findByIdWithCustomer(id).ifPresent(s -> {
                if (!s.getCustomer().getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot delete site owned by another user: " + id);
                }
                if (s.getDeletedAt() == null) {
                    s.setDeletedAt(now);
                    s.setVersion(s.getVersion() + 1);
                    siteRepository.save(s);

                    cascadeSoftDeleteSiteChildren(id, now);
                }
            });
        }
    }

    private void cascadeSoftDeleteSiteChildren(UUID siteId, Instant now) {
        List<FieldNote> notes = fieldNoteRepository.findAllBySiteIdAndDeletedAtIsNull(siteId);
        for (FieldNote note : notes) {
            note.setDeletedAt(now);
            note.setVersion(note.getVersion() + 1);
            fieldNoteRepository.save(note);
        }
    }

    // =========================================================================
    // Push helpers — field notes
    // =========================================================================

    private void applyFieldNoteUpserts(User user, List<FieldNoteSyncItem> items, List<SyncConflict> conflicts) {
        if (items == null || items.isEmpty()) return;

        for (FieldNoteSyncItem item : items) {
            Optional<FieldNote> existing = fieldNoteRepository.findById(item.id());

            if (existing.isEmpty()) {
                Site site = siteRepository.findByIdWithCustomer(item.siteId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Site not found for field-note sync: " + item.siteId()));
                if (!site.getCustomer().getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot sync field note under site owned by another user: " + item.siteId());
                }
                if (site.getDeletedAt() != null) {
                    conflicts.add(new SyncConflict("fieldNote", item.id(),
                            "Parent site is soft-deleted: " + item.siteId()));
                    continue;
                }
                FieldNote fn = new FieldNote();
                fn.setId(item.id());
                fn.setSite(site);
                applyFieldNoteFields(fn, item);
                fn.setVersion(0);
                fieldNoteRepository.save(fn);
            } else {
                FieldNote fn = existing.get();
                if (!fn.getSite().getCustomer().getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot sync field note owned by another user: " + item.id());
                }
                if (fn.getDeletedAt() != null) {
                    conflicts.add(new SyncConflict("fieldNote", item.id(),
                            "Record is soft-deleted on the server"));
                    continue;
                }
                if (fn.getSite().getDeletedAt() != null) {
                    conflicts.add(new SyncConflict("fieldNote", item.id(),
                            "Parent site is soft-deleted: " + fn.getSite().getId()));
                    continue;
                }
                if (isFieldNoteContentEqual(fn, item)) {
                    // Already applied — idempotent no-op
                    continue;
                }
                if (fn.getVersion() > item.version()) {
                    conflicts.add(new SyncConflict("fieldNote", item.id(),
                            "Server version (" + fn.getVersion() + ") is ahead of client version (" + item.version() + ")"));
                    continue;
                }
                applyFieldNoteFields(fn, item);
                fn.setVersion(Math.max(fn.getVersion(), item.version()) + 1);
                fieldNoteRepository.save(fn);
            }
        }
    }

    private boolean isFieldNoteContentEqual(FieldNote fn, FieldNoteSyncItem item) {
        return Objects.equals(fn.getTitle(), item.title().trim())
                && Objects.equals(fn.getDescription(), item.description().trim())
                && Objects.equals(fn.getStatus(), item.status() != null ? item.status() : NoteStatus.PENDING)
                && ((fn.getLatitude() == null && item.latitude() == null) || (fn.getLatitude() != null && item.latitude() != null && fn.getLatitude().compareTo(item.latitude()) == 0))
                && ((fn.getLongitude() == null && item.longitude() == null) || (fn.getLongitude() != null && item.longitude() != null && fn.getLongitude().compareTo(item.longitude()) == 0))
                && Objects.equals(fn.getPhotoReference(), item.photoReference() == null ? null : item.photoReference().trim());
    }

    private void applyFieldNoteFields(FieldNote fn, FieldNoteSyncItem item) {
        fn.setTitle(item.title().trim());
        fn.setDescription(item.description().trim());
        fn.setNoteDate(item.noteDate() != null ? item.noteDate() : Instant.now());
        fn.setStatus(item.status() != null ? item.status() : NoteStatus.PENDING);
        fn.setLatitude(item.latitude());
        fn.setLongitude(item.longitude());
        fn.setPhotoReference(item.photoReference() == null ? null : item.photoReference().trim());
    }

    private void applyFieldNoteDeletes(User user, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (UUID id : ids) {
            fieldNoteRepository.findByIdWithSiteAndCustomer(id).ifPresent(fn -> {
                if (!fn.getSite().getCustomer().getUser().getId().equals(user.getId())) {
                    throw new UnauthorizedOperationException(
                            "Cannot delete field note owned by another user: " + id);
                }
                if (fn.getDeletedAt() == null) {
                    fn.setDeletedAt(Instant.now());
                    fn.setVersion(fn.getVersion() + 1);
                    fieldNoteRepository.save(fn);
                }
            });
        }
    }

    // =========================================================================
    // Pull helpers
    // =========================================================================

    private List<CustomerResponse> pullCustomers(User user, Instant since) {
        return customerRepository
                .findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(user.getId(), since, Pageable.unpaged())
                .stream()
                .map(CustomerResponse::from)
                .toList();
    }

    private List<SiteResponse> pullSites(User user, Instant since) {
        return siteRepository
                .findAllByCustomerUserIdAndUpdatedAtAfterAndDeletedAtIsNull(user.getId(), since, Pageable.unpaged())
                .stream()
                .map(SiteResponse::from)
                .toList();
    }

    private List<FieldNoteResponse> pullFieldNotes(User user, Instant since) {
        return fieldNoteRepository
                .findAllBySiteCustomerUserIdAndUpdatedAtAfterAndDeletedAtIsNull(user.getId(), since, Pageable.unpaged())
                .stream()
                .map(FieldNoteResponse::from)
                .toList();
    }

    private List<UUID> pullDeletedCustomerIds(User user, Instant since) {
        return customerRepository
                .findAllByUserIdAndDeletedAtAfter(user.getId(), since)
                .stream()
                .map(Customer::getId)
                .toList();
    }

    private List<UUID> pullDeletedSiteIds(User user, Instant since) {
        return siteRepository
                .findAllByCustomerUserIdAndDeletedAtAfter(user.getId(), since)
                .stream()
                .map(Site::getId)
                .toList();
    }

    private List<UUID> pullDeletedFieldNoteIds(User user, Instant since) {
        return fieldNoteRepository
                .findAllBySiteCustomerUserIdAndDeletedAtAfter(user.getId(), since)
                .stream()
                .map(FieldNote::getId)
                .toList();
    }
}

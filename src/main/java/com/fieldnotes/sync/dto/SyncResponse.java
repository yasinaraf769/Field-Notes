package com.fieldnotes.sync.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fieldnotes.customer.dto.CustomerResponse;
import com.fieldnotes.fieldnote.dto.FieldNoteResponse;
import com.fieldnotes.site.dto.SiteResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response payload for POST /api/fieldnotesatl/sync.
 *
 * <p>Contains:
 * <ul>
 *   <li>the new cursor the client should persist and send on the next sync</li>
 *   <li>all records changed on the server since the client's previous cursor</li>
 *   <li>UUIDs of records the server has soft-deleted since the client's previous cursor</li>
 *   <li>any push conflicts detected during the request</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncResponse(

        /**
         * ISO-8601 timestamp the client must send as {@code lastSyncAt} on its next
         * call.  Corresponds to the server-assigned {@code updated_at} ceiling returned
         * in this batch.
         */
        Instant newCursor,

        /** Changed/new customers since the previous cursor. */
        List<CustomerResponse> customers,

        /** Changed/new sites since the previous cursor. */
        List<SiteResponse> sites,

        /** Changed/new field notes since the previous cursor. */
        List<FieldNoteResponse> fieldNotes,

        /** Customers soft-deleted on the server since the previous cursor. */
        List<UUID> deletedCustomerIds,

        /** Sites soft-deleted on the server since the previous cursor. */
        List<UUID> deletedSiteIds,

        /** Field notes soft-deleted on the server since the previous cursor. */
        List<UUID> deletedFieldNoteIds,

        /**
         * Conflicts detected while applying client changes.  Normally empty;
         * populated only when a client-pushed record version is behind the server
         * version and both sides differ.
         */
        List<SyncConflict> conflicts
) {
    /** Convenience builder for the common success path. */
    public static SyncResponse of(
            Instant newCursor,
            List<CustomerResponse> customers,
            List<SiteResponse> sites,
            List<FieldNoteResponse> fieldNotes,
            List<UUID> deletedCustomerIds,
            List<UUID> deletedSiteIds,
            List<UUID> deletedFieldNoteIds,
            List<SyncConflict> conflicts) {
        return new SyncResponse(
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
}

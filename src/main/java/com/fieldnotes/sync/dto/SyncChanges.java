package com.fieldnotes.sync.dto;

import java.util.List;
import java.util.UUID;

/**
 * The batch of local writes the client wants to push in a single sync call.
 *
 * <p>All three lists are optional (null = no changes of that type).
 * Each list entry carries the full client-side state of the entity; the
 * server will create or update as appropriate.
 */
public record SyncChanges(

        /** Customer creates/updates (upsert semantics keyed by {@code id}). */
        List<CustomerSyncItem> customers,

        /** Site creates/updates (upsert semantics keyed by {@code id}). */
        List<SiteSyncItem> sites,

        /** Field-note creates/updates (upsert semantics keyed by {@code id}). */
        List<FieldNoteSyncItem> fieldNotes,

        /**
         * IDs of customers the client has deleted locally.
         * The server performs a soft-delete; the record remains visible as a tombstone.
         */
        List<UUID> deletedCustomerIds,

        /**
         * IDs of sites the client has deleted locally.
         */
        List<UUID> deletedSiteIds,

        /**
         * IDs of field notes the client has deleted locally.
         */
        List<UUID> deletedFieldNoteIds
) {}

package com.fieldnotes.sync.dto;

import java.util.UUID;

/**
 * Describes a conflict detected while applying a client-pushed change.
 *
 * <p>A conflict occurs when:
 * <ul>
 *   <li>the client sends a version {@code n} for an existing record, and</li>
 *   <li>the server already has version {@code > n} for that record, and</li>
 *   <li>the client's field values differ from the server's current values.</li>
 * </ul>
 *
 * <p>The conflicting record is returned in the pull section of the response so
 * the client can present a resolution UI.
 */
public record SyncConflict(

        /** The type of entity in conflict: "customer", "site", or "fieldNote". */
        String entityType,

        /** The primary-key UUID of the conflicting record. */
        UUID entityId,

        /**
         * Human-readable reason for the conflict, e.g.
         * "Server version (3) is ahead of client version (1)".
         */
        String reason
) {}

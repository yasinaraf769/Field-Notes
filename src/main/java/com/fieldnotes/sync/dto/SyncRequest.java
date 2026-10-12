package com.fieldnotes.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request payload for POST /api/fieldnotesatl/sync.
 *
 * <p>The client sends:
 * <ul>
 *   <li>a stable {@code deviceId} (opaque string, e.g. Flutter {@code deviceInfo} ID)</li>
 *   <li>the {@code lastSyncAt} cursor it received in the previous response
 *       (or {@code null} / epoch for a full initial pull)</li>
 *   <li>any local changes made since the last successful sync</li>
 * </ul>
 */
public record SyncRequest(

        /**
         * Stable, opaque device identifier.  Used server-side to maintain a
         * per-(user, device) sync cursor.
         */
        @NotBlank(message = "deviceId must not be blank")
        @Size(max = 255, message = "deviceId must be at most 255 characters")
        String deviceId,

        /**
         * The cursor returned by the previous successful sync call.
         * {@code null} or omitted means "give me everything" (full initial pull).
         */
        Instant lastSyncAt,

        /**
         * Local changes to push to the server.  May be {@code null} or empty
         * when the client only wants to pull.
         */
        @Valid
        SyncChanges changes
) {}

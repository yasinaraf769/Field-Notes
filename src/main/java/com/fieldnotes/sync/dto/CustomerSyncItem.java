package com.fieldnotes.sync.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A single customer record pushed by the client during sync.
 *
 * <p>The {@code id} must be a client-generated UUID that is stable across
 * offline/online transitions.  The server uses this UUID as the primary key.
 */
public record CustomerSyncItem(

        /** Client-generated UUID — must be stable. */
        @NotNull(message = "Customer id must not be null")
        UUID id,

        @NotBlank(message = "Customer name must not be blank")
        @Size(max = 255)
        String name,

        @Size(max = 255)
        String contactName,

        @Size(max = 50)
        String contactPhone,

        @Email
        @Size(max = 255)
        String contactEmail,

        String notes,

        /**
         * The client's local version counter for this record.
         * Used for optimistic conflict detection.
         */
        long version
) {}

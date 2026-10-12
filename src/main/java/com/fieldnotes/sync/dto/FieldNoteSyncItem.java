package com.fieldnotes.sync.dto;

import com.fieldnotes.fieldnote.entity.NoteStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single field-note record pushed by the client during sync.
 */
public record FieldNoteSyncItem(

        /** Client-generated UUID — must be stable. */
        @NotNull(message = "FieldNote id must not be null")
        UUID id,

        @NotNull(message = "siteId must not be null")
        UUID siteId,

        @NotBlank(message = "Title must not be blank")
        @Size(max = 255)
        String title,

        @NotBlank(message = "Description must not be blank")
        String description,

        /** When the note was taken.  Defaults to server time if null. */
        Instant noteDate,

        NoteStatus status,

        BigDecimal latitude,

        BigDecimal longitude,

        @Size(max = 500)
        String photoReference,

        /** The client's local version counter — used for conflict detection. */
        long version
) {}

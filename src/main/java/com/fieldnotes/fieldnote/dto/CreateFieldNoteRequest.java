package com.fieldnotes.fieldnote.dto;

import com.fieldnotes.fieldnote.entity.NoteStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateFieldNoteRequest(
        UUID id,

        @NotNull(message = "Site ID is required")
        UUID siteId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        Instant noteDate,

        NoteStatus status,

        BigDecimal latitude,
        BigDecimal longitude,

        @Size(max = 500, message = "Photo reference must be at most 500 characters")
        String photoReference
) {}

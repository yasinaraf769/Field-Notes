package com.fieldnotes.fieldnote.dto;

import com.fieldnotes.fieldnote.entity.NoteStatus;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record PatchFieldNoteRequest(
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        String description,

        Instant noteDate,

        NoteStatus status,

        BigDecimal latitude,
        BigDecimal longitude,

        @Size(max = 500, message = "Photo reference must be at most 500 characters")
        String photoReference
) {}

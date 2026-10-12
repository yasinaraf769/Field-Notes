package com.fieldnotes.fieldnote.dto;

import com.fieldnotes.fieldnote.entity.FieldNote;
import com.fieldnotes.fieldnote.entity.NoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FieldNoteResponse(
        UUID id,
        UUID siteId,
        String siteName,
        UUID customerId,
        String customerName,
        String title,
        String description,
        Instant noteDate,
        NoteStatus status,
        BigDecimal latitude,
        BigDecimal longitude,
        String photoReference,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static FieldNoteResponse from(FieldNote note) {
        return new FieldNoteResponse(
                note.getId(),
                note.getSite().getId(),
                note.getSite().getName(),
                note.getSite().getCustomer().getId(),
                note.getSite().getCustomer().getName(),
                note.getTitle(),
                note.getDescription(),
                note.getNoteDate(),
                note.getStatus(),
                note.getLatitude(),
                note.getLongitude(),
                note.getPhotoReference(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                note.getVersion()
        );
    }
}

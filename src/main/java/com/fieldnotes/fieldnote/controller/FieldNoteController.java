package com.fieldnotes.fieldnote.controller;

import com.fieldnotes.common.response.PageResponse;
import com.fieldnotes.fieldnote.dto.CreateFieldNoteRequest;
import com.fieldnotes.fieldnote.dto.FieldNoteResponse;
import com.fieldnotes.fieldnote.dto.PatchFieldNoteRequest;
import com.fieldnotes.fieldnote.dto.UpdateFieldNoteRequest;
import com.fieldnotes.fieldnote.entity.NoteStatus;
import com.fieldnotes.fieldnote.service.FieldNoteService;
import com.fieldnotes.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/fieldnotesatl/field-notes")
public class FieldNoteController {

    private final FieldNoteService fieldNoteService;

    public FieldNoteController(FieldNoteService fieldNoteService) {
        this.fieldNoteService = fieldNoteService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<FieldNoteResponse>> getFieldNotes(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "noteDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(fieldNoteService.getFieldNotes(user, siteId, customerId, status, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FieldNoteResponse> getFieldNoteById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(fieldNoteService.getFieldNoteById(user, id));
    }

    @PostMapping
    public ResponseEntity<FieldNoteResponse> createFieldNote(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateFieldNoteRequest request) {
        FieldNoteResponse response = fieldNoteService.createFieldNote(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FieldNoteResponse> updateFieldNote(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFieldNoteRequest request) {
        return ResponseEntity.ok(fieldNoteService.updateFieldNote(user, id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FieldNoteResponse> patchFieldNote(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody PatchFieldNoteRequest request) {
        return ResponseEntity.ok(fieldNoteService.patchFieldNote(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFieldNote(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        fieldNoteService.deleteFieldNote(user, id);
        return ResponseEntity.noContent().build();
    }
}

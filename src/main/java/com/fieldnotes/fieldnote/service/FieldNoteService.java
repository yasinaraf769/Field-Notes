package com.fieldnotes.fieldnote.service;

import com.fieldnotes.common.exception.ResourceNotFoundException;
import com.fieldnotes.common.exception.UnauthorizedOperationException;
import com.fieldnotes.common.response.PageResponse;
import com.fieldnotes.fieldnote.dto.CreateFieldNoteRequest;
import com.fieldnotes.fieldnote.dto.FieldNoteResponse;
import com.fieldnotes.fieldnote.dto.PatchFieldNoteRequest;
import com.fieldnotes.fieldnote.dto.UpdateFieldNoteRequest;
import com.fieldnotes.fieldnote.entity.FieldNote;
import com.fieldnotes.fieldnote.entity.NoteStatus;
import com.fieldnotes.fieldnote.repository.FieldNoteRepository;
import com.fieldnotes.site.entity.Site;
import com.fieldnotes.site.repository.SiteRepository;
import com.fieldnotes.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class FieldNoteService {

    private final FieldNoteRepository fieldNoteRepository;
    private final SiteRepository siteRepository;

    public FieldNoteService(FieldNoteRepository fieldNoteRepository, SiteRepository siteRepository) {
        this.fieldNoteRepository = fieldNoteRepository;
        this.siteRepository = siteRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<FieldNoteResponse> getFieldNotes(
            User user,
            UUID siteId,
            UUID customerId,
            NoteStatus status,
            String search,
            Pageable pageable) {

        String trimmedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<FieldNote> page = fieldNoteRepository.findFieldNotes(
                user.getId(),
                siteId,
                customerId,
                status,
                trimmedSearch,
                pageable
        );

        return PageResponse.from(page.map(FieldNoteResponse::from));
    }

    @Transactional(readOnly = true)
    public FieldNoteResponse getFieldNoteById(User user, UUID noteId) {
        FieldNote note = findNoteOrCheckOwnership(user, noteId);
        return FieldNoteResponse.from(note);
    }

    @Transactional
    public FieldNoteResponse createFieldNote(User user, CreateFieldNoteRequest request) {
        Site site = siteRepository.findByIdWithCustomer(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + request.siteId()));

        if (site.getDeletedAt() != null || site.getCustomer().getDeletedAt() != null) {
            throw new ResourceNotFoundException("Cannot add note to deleted site or customer: " + request.siteId());
        }

        if (!site.getCustomer().getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("Cannot add note to a site owned by another user");
        }

        UUID noteId = request.id() != null ? request.id() : UUID.randomUUID();
        if (fieldNoteRepository.existsById(noteId)) {
            throw new IllegalArgumentException("Field note with ID " + noteId + " already exists");
        }

        FieldNote note = new FieldNote();
        note.setId(noteId);
        note.setSite(site);
        note.setTitle(request.title().trim());
        note.setDescription(request.description().trim());
        note.setNoteDate(request.noteDate() != null ? request.noteDate() : Instant.now());
        note.setStatus(request.status() != null ? request.status() : NoteStatus.PENDING);
        note.setLatitude(request.latitude());
        note.setLongitude(request.longitude());
        note.setPhotoReference(request.photoReference() == null ? null : request.photoReference().trim());
        note.setVersion(0);

        FieldNote saved = fieldNoteRepository.save(note);
        return FieldNoteResponse.from(saved);
    }

    @Transactional
    public FieldNoteResponse updateFieldNote(User user, UUID noteId, UpdateFieldNoteRequest request) {
        FieldNote note = findNoteOrCheckOwnership(user, noteId);

        note.setTitle(request.title().trim());
        note.setDescription(request.description().trim());
        note.setNoteDate(request.noteDate());
        note.setStatus(request.status());
        note.setLatitude(request.latitude());
        note.setLongitude(request.longitude());
        note.setPhotoReference(request.photoReference() == null ? null : request.photoReference().trim());
        note.setVersion(note.getVersion() + 1);

        FieldNote updated = fieldNoteRepository.save(note);
        return FieldNoteResponse.from(updated);
    }

    @Transactional
    public FieldNoteResponse patchFieldNote(User user, UUID noteId, PatchFieldNoteRequest request) {
        FieldNote note = findNoteOrCheckOwnership(user, noteId);

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new IllegalArgumentException("Title cannot be blank");
            }
            note.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            if (request.description().isBlank()) {
                throw new IllegalArgumentException("Description cannot be blank");
            }
            note.setDescription(request.description().trim());
        }
        if (request.noteDate() != null) {
            note.setNoteDate(request.noteDate());
        }
        if (request.status() != null) {
            note.setStatus(request.status());
        }
        if (request.latitude() != null) {
            note.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            note.setLongitude(request.longitude());
        }
        if (request.photoReference() != null) {
            note.setPhotoReference(request.photoReference().trim());
        }

        note.setVersion(note.getVersion() + 1);

        FieldNote updated = fieldNoteRepository.save(note);
        return FieldNoteResponse.from(updated);
    }

    @Transactional
    public void deleteFieldNote(User user, UUID noteId) {
        FieldNote note = findNoteOrCheckOwnership(user, noteId);

        note.setDeletedAt(Instant.now());
        note.setVersion(note.getVersion() + 1);
        fieldNoteRepository.save(note);
    }

    private FieldNote findNoteOrCheckOwnership(User user, UUID noteId) {
        FieldNote note = fieldNoteRepository.findByIdWithSiteAndCustomer(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Field note not found: " + noteId));

        if (note.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Field note not found: " + noteId);
        }

        if (!note.getSite().getCustomer().getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("You do not have permission to access field note: " + noteId);
        }

        return note;
    }
}

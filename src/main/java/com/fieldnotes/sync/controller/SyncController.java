package com.fieldnotes.sync.controller;

import com.fieldnotes.sync.dto.SyncRequest;
import com.fieldnotes.sync.dto.SyncResponse;
import com.fieldnotes.sync.service.SyncService;
import com.fieldnotes.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the offline-first delta synchronization endpoint.
 *
 * <p>Base path is required to be {@code /api/fieldnotesatl/sync}.
 */
@RestController
@RequestMapping("/api/fieldnotesatl/sync")
@Tag(name = "Sync", description = "Offline-first delta synchronization endpoints")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    @Operation(summary = "Synchronize local offline changes and pull latest server deltas")
    public ResponseEntity<SyncResponse> sync(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SyncRequest request) {
        SyncResponse response = syncService.sync(user, request);
        return ResponseEntity.ok(response);
    }
}

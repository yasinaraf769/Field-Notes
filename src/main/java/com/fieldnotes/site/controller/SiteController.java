package com.fieldnotes.site.controller;

import com.fieldnotes.common.response.PageResponse;
import com.fieldnotes.site.dto.CreateSiteRequest;
import com.fieldnotes.site.dto.PatchSiteRequest;
import com.fieldnotes.site.dto.SiteResponse;
import com.fieldnotes.site.dto.UpdateSiteRequest;
import com.fieldnotes.site.service.SiteService;
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
@RequestMapping("/api/fieldnotesatl/sites")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SiteResponse>> getSites(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(siteService.getSites(user, customerId, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SiteResponse> getSiteById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(siteService.getSiteById(user, id));
    }

    @PostMapping
    public ResponseEntity<SiteResponse> createSite(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateSiteRequest request) {
        SiteResponse response = siteService.createSite(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SiteResponse> updateSite(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSiteRequest request) {
        return ResponseEntity.ok(siteService.updateSite(user, id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SiteResponse> patchSite(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody PatchSiteRequest request) {
        return ResponseEntity.ok(siteService.patchSite(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSite(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        siteService.deleteSite(user, id);
        return ResponseEntity.noContent().build();
    }
}

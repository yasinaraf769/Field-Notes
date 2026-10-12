package com.fieldnotes.site.service;

import com.fieldnotes.common.exception.ResourceNotFoundException;
import com.fieldnotes.common.exception.UnauthorizedOperationException;
import com.fieldnotes.common.response.PageResponse;
import com.fieldnotes.customer.entity.Customer;
import com.fieldnotes.customer.repository.CustomerRepository;
import com.fieldnotes.fieldnote.entity.FieldNote;
import com.fieldnotes.fieldnote.repository.FieldNoteRepository;
import com.fieldnotes.site.dto.CreateSiteRequest;
import com.fieldnotes.site.dto.PatchSiteRequest;
import com.fieldnotes.site.dto.SiteResponse;
import com.fieldnotes.site.dto.UpdateSiteRequest;
import com.fieldnotes.site.entity.Site;
import com.fieldnotes.site.repository.SiteRepository;
import com.fieldnotes.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final CustomerRepository customerRepository;
    private final FieldNoteRepository fieldNoteRepository;

    public SiteService(SiteRepository siteRepository,
                       CustomerRepository customerRepository,
                       FieldNoteRepository fieldNoteRepository) {
        this.siteRepository = siteRepository;
        this.customerRepository = customerRepository;
        this.fieldNoteRepository = fieldNoteRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SiteResponse> getSites(User user, UUID customerId, String search, Pageable pageable) {
        Page<Site> page;
        if (search != null && !search.isBlank()) {
            page = siteRepository.searchSites(user.getId(), customerId, search.trim(), pageable);
        } else {
            page = siteRepository.findAllByCustomerUserIdAndOptionalCustomerId(user.getId(), customerId, pageable);
        }
        return PageResponse.from(page.map(SiteResponse::from));
    }

    @Transactional(readOnly = true)
    public SiteResponse getSiteById(User user, UUID siteId) {
        Site site = findSiteOrCheckOwnership(user, siteId);
        return SiteResponse.from(site);
    }

    @Transactional
    public SiteResponse createSite(User user, CreateSiteRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.customerId()));

        if (customer.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Cannot create site under deleted customer: " + request.customerId());
        }

        if (!customer.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("Cannot add site to customer owned by another user");
        }

        UUID siteId = request.id() != null ? request.id() : UUID.randomUUID();
        if (siteRepository.existsById(siteId)) {
            throw new IllegalArgumentException("Site with ID " + siteId + " already exists");
        }

        Site site = new Site();
        site.setId(siteId);
        site.setCustomer(customer);
        site.setName(request.name().trim());
        site.setAddressLine1(request.addressLine1().trim());
        site.setAddressLine2(request.addressLine2() == null ? null : request.addressLine2().trim());
        site.setCity(request.city() == null ? null : request.city().trim());
        site.setStateProvince(request.stateProvince() == null ? null : request.stateProvince().trim());
        site.setPostalCode(request.postalCode() == null ? null : request.postalCode().trim());
        site.setCountry(request.country() == null ? null : request.country().trim());
        site.setLatitude(request.latitude());
        site.setLongitude(request.longitude());
        site.setVersion(0);

        Site saved = siteRepository.save(site);
        return SiteResponse.from(saved);
    }

    @Transactional
    public SiteResponse updateSite(User user, UUID siteId, UpdateSiteRequest request) {
        Site site = findSiteOrCheckOwnership(user, siteId);

        site.setName(request.name().trim());
        site.setAddressLine1(request.addressLine1().trim());
        site.setAddressLine2(request.addressLine2() == null ? null : request.addressLine2().trim());
        site.setCity(request.city() == null ? null : request.city().trim());
        site.setStateProvince(request.stateProvince() == null ? null : request.stateProvince().trim());
        site.setPostalCode(request.postalCode() == null ? null : request.postalCode().trim());
        site.setCountry(request.country() == null ? null : request.country().trim());
        site.setLatitude(request.latitude());
        site.setLongitude(request.longitude());
        site.setVersion(site.getVersion() + 1);

        Site updated = siteRepository.save(site);
        return SiteResponse.from(updated);
    }

    @Transactional
    public SiteResponse patchSite(User user, UUID siteId, PatchSiteRequest request) {
        Site site = findSiteOrCheckOwnership(user, siteId);

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Site name cannot be blank");
            }
            site.setName(request.name().trim());
        }
        if (request.addressLine1() != null) {
            if (request.addressLine1().isBlank()) {
                throw new IllegalArgumentException("Address line 1 cannot be blank");
            }
            site.setAddressLine1(request.addressLine1().trim());
        }
        if (request.addressLine2() != null) {
            site.setAddressLine2(request.addressLine2().trim());
        }
        if (request.city() != null) {
            site.setCity(request.city().trim());
        }
        if (request.stateProvince() != null) {
            site.setStateProvince(request.stateProvince().trim());
        }
        if (request.postalCode() != null) {
            site.setPostalCode(request.postalCode().trim());
        }
        if (request.country() != null) {
            site.setCountry(request.country().trim());
        }
        if (request.latitude() != null) {
            site.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            site.setLongitude(request.longitude());
        }

        site.setVersion(site.getVersion() + 1);

        Site updated = siteRepository.save(site);
        return SiteResponse.from(updated);
    }

    @Transactional
    public void deleteSite(User user, UUID siteId) {
        Site site = findSiteOrCheckOwnership(user, siteId);

        Instant now = Instant.now();
        site.setDeletedAt(now);
        site.setVersion(site.getVersion() + 1);
        siteRepository.save(site);

        // Cascade soft-delete to field notes
        cascadeSoftDeleteSiteChildren(siteId, now);
    }

    private void cascadeSoftDeleteSiteChildren(UUID siteId, Instant now) {
        List<FieldNote> notes = fieldNoteRepository.findAllBySiteIdAndDeletedAtIsNull(siteId);
        for (FieldNote note : notes) {
            note.setDeletedAt(now);
            note.setVersion(note.getVersion() + 1);
            fieldNoteRepository.save(note);
        }
    }

    private Site findSiteOrCheckOwnership(User user, UUID siteId) {
        Site site = siteRepository.findByIdWithCustomer(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + siteId));

        if (site.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Site not found: " + siteId);
        }

        if (!site.getCustomer().getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("You do not have permission to access site: " + siteId);
        }

        return site;
    }
}

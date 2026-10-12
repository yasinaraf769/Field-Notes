package com.fieldnotes.customer.service;

import com.fieldnotes.common.exception.ResourceNotFoundException;
import com.fieldnotes.common.exception.UnauthorizedOperationException;
import com.fieldnotes.common.response.PageResponse;
import com.fieldnotes.customer.dto.CreateCustomerRequest;
import com.fieldnotes.customer.dto.CustomerResponse;
import com.fieldnotes.customer.dto.PatchCustomerRequest;
import com.fieldnotes.customer.dto.UpdateCustomerRequest;
import com.fieldnotes.customer.entity.Customer;
import com.fieldnotes.customer.repository.CustomerRepository;
import com.fieldnotes.fieldnote.entity.FieldNote;
import com.fieldnotes.fieldnote.repository.FieldNoteRepository;
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
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final SiteRepository siteRepository;
    private final FieldNoteRepository fieldNoteRepository;

    public CustomerService(CustomerRepository customerRepository,
                           SiteRepository siteRepository,
                           FieldNoteRepository fieldNoteRepository) {
        this.customerRepository = customerRepository;
        this.siteRepository = siteRepository;
        this.fieldNoteRepository = fieldNoteRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getCustomers(User user, String search, Pageable pageable) {
        Page<Customer> page;
        if (search != null && !search.isBlank()) {
            page = customerRepository.searchCustomers(user.getId(), search.trim(), pageable);
        } else {
            page = customerRepository.findAllByUserIdAndDeletedAtIsNull(user.getId(), pageable);
        }
        return PageResponse.from(page.map(CustomerResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(User user, UUID customerId) {
        Customer customer = findCustomerOrCheckOwnership(user, customerId);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse createCustomer(User user, CreateCustomerRequest request) {
        UUID customerId = request.id() != null ? request.id() : UUID.randomUUID();

        if (customerRepository.existsById(customerId)) {
            throw new IllegalArgumentException("Customer with ID " + customerId + " already exists");
        }

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setUser(user);
        customer.setName(request.name().trim());
        customer.setContactName(request.contactName() == null ? null : request.contactName().trim());
        customer.setContactPhone(request.contactPhone() == null ? null : request.contactPhone().trim());
        customer.setContactEmail(request.contactEmail() == null ? null : request.contactEmail().trim().toLowerCase());
        customer.setNotes(request.notes());
        customer.setVersion(0);

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse updateCustomer(User user, UUID customerId, UpdateCustomerRequest request) {
        Customer customer = findCustomerOrCheckOwnership(user, customerId);

        customer.setName(request.name().trim());
        customer.setContactName(request.contactName() == null ? null : request.contactName().trim());
        customer.setContactPhone(request.contactPhone() == null ? null : request.contactPhone().trim());
        customer.setContactEmail(request.contactEmail() == null ? null : request.contactEmail().trim().toLowerCase());
        customer.setNotes(request.notes());
        customer.setVersion(customer.getVersion() + 1);

        Customer updated = customerRepository.save(customer);
        return CustomerResponse.from(updated);
    }

    @Transactional
    public CustomerResponse patchCustomer(User user, UUID customerId, PatchCustomerRequest request) {
        Customer customer = findCustomerOrCheckOwnership(user, customerId);

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Customer name cannot be blank");
            }
            customer.setName(request.name().trim());
        }
        if (request.contactName() != null) {
            customer.setContactName(request.contactName().trim());
        }
        if (request.contactPhone() != null) {
            customer.setContactPhone(request.contactPhone().trim());
        }
        if (request.contactEmail() != null) {
            customer.setContactEmail(request.contactEmail().trim().toLowerCase());
        }
        if (request.notes() != null) {
            customer.setNotes(request.notes());
        }

        customer.setVersion(customer.getVersion() + 1);

        Customer updated = customerRepository.save(customer);
        return CustomerResponse.from(updated);
    }

    @Transactional
    public void deleteCustomer(User user, UUID customerId) {
        Customer customer = findCustomerOrCheckOwnership(user, customerId);

        Instant now = Instant.now();
        customer.setDeletedAt(now);
        customer.setVersion(customer.getVersion() + 1);
        customerRepository.save(customer);

        // Cascade soft-delete to sites and field notes
        cascadeSoftDeleteCustomerChildren(customerId, now);
    }

    private void cascadeSoftDeleteCustomerChildren(UUID customerId, Instant now) {
        List<Site> sites = siteRepository.findAllByCustomerIdAndDeletedAtIsNull(customerId);
        for (Site site : sites) {
            site.setDeletedAt(now);
            site.setVersion(site.getVersion() + 1);
            siteRepository.save(site);

            List<FieldNote> notes = fieldNoteRepository.findAllBySiteIdAndDeletedAtIsNull(site.getId());
            for (FieldNote note : notes) {
                note.setDeletedAt(now);
                note.setVersion(note.getVersion() + 1);
                fieldNoteRepository.save(note);
            }
        }
    }

    private Customer findCustomerOrCheckOwnership(User user, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

        if (customer.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }

        if (!customer.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException("You do not have permission to access customer: " + customerId);
        }

        return customer;
    }
}

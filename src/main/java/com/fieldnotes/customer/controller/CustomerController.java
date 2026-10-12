package com.fieldnotes.customer.controller;

import com.fieldnotes.common.response.PageResponse;
import com.fieldnotes.customer.dto.CreateCustomerRequest;
import com.fieldnotes.customer.dto.CustomerResponse;
import com.fieldnotes.customer.dto.PatchCustomerRequest;
import com.fieldnotes.customer.dto.UpdateCustomerRequest;
import com.fieldnotes.customer.service.CustomerService;
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
@RequestMapping("/api/fieldnotesatl/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponse>> getCustomers(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(customerService.getCustomers(user, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerById(user, id));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(user, id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CustomerResponse> patchCustomer(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody PatchCustomerRequest request) {
        return ResponseEntity.ok(customerService.patchCustomer(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        customerService.deleteCustomer(user, id);
        return ResponseEntity.noContent().build();
    }
}

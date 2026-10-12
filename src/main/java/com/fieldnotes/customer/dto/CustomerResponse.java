package com.fieldnotes.customer.dto;

import com.fieldnotes.customer.entity.Customer;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String contactName,
        String contactPhone,
        String contactEmail,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getContactName(),
                customer.getContactPhone(),
                customer.getContactEmail(),
                customer.getNotes(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getVersion()
        );
    }
}

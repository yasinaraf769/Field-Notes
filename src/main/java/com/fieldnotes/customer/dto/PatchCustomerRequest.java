package com.fieldnotes.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record PatchCustomerRequest(
        @Size(max = 255, message = "Customer name must be at most 255 characters")
        String name,

        @Size(max = 255, message = "Contact name must be at most 255 characters")
        String contactName,

        @Size(max = 50, message = "Contact phone must be at most 50 characters")
        String contactPhone,

        @Email(message = "Contact email must be a valid email address")
        @Size(max = 255, message = "Contact email must be at most 255 characters")
        String contactEmail,

        String notes
) {}

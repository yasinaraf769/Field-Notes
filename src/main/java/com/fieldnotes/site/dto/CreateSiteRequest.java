package com.fieldnotes.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSiteRequest(
        UUID id,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotBlank(message = "Site name is required")
        @Size(max = 255, message = "Site name must be at most 255 characters")
        String name,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must be at most 255 characters")
        String addressLine1,

        @Size(max = 255, message = "Address line 2 must be at most 255 characters")
        String addressLine2,

        @Size(max = 120, message = "City must be at most 120 characters")
        String city,

        @Size(max = 120, message = "State/Province must be at most 120 characters")
        String stateProvince,

        @Size(max = 30, message = "Postal code must be at most 30 characters")
        String postalCode,

        @Size(max = 120, message = "Country must be at most 120 characters")
        String country,

        BigDecimal latitude,
        BigDecimal longitude
) {}

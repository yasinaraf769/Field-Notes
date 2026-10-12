package com.fieldnotes.site.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PatchSiteRequest(
        @Size(max = 255, message = "Site name must be at most 255 characters")
        String name,

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

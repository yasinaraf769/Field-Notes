package com.fieldnotes.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single site record pushed by the client during sync.
 */
public record SiteSyncItem(

        /** Client-generated UUID — must be stable. */
        @NotNull(message = "Site id must not be null")
        UUID id,

        /** The customer this site belongs to. */
        @NotNull(message = "customerId must not be null")
        UUID customerId,

        @NotBlank(message = "Site name must not be blank")
        @Size(max = 255)
        String name,

        @NotBlank(message = "addressLine1 must not be blank")
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @Size(max = 120)
        String city,

        @Size(max = 120)
        String stateProvince,

        @Size(max = 30)
        String postalCode,

        @Size(max = 120)
        String country,

        BigDecimal latitude,

        BigDecimal longitude,

        /** The client's local version counter — used for conflict detection. */
        long version
) {}

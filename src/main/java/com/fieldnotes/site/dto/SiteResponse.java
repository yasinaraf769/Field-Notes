package com.fieldnotes.site.dto;

import com.fieldnotes.site.entity.Site;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SiteResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String stateProvince,
        String postalCode,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static SiteResponse from(Site site) {
        return new SiteResponse(
                site.getId(),
                site.getCustomer().getId(),
                site.getCustomer().getName(),
                site.getName(),
                site.getAddressLine1(),
                site.getAddressLine2(),
                site.getCity(),
                site.getStateProvince(),
                site.getPostalCode(),
                site.getCountry(),
                site.getLatitude(),
                site.getLongitude(),
                site.getCreatedAt(),
                site.getUpdatedAt(),
                site.getVersion()
        );
    }
}

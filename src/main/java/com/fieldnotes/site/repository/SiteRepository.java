package com.fieldnotes.site.repository;

import com.fieldnotes.site.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    List<Site> findAllByCustomerIdAndDeletedAtIsNull(UUID customerId);

    @Query("""
        SELECT s FROM Site s
        JOIN FETCH s.customer c
        WHERE c.user.id = :userId
          AND s.updatedAt > :since
          AND s.deletedAt IS NULL
        ORDER BY s.updatedAt ASC
    """)
    List<Site> findAllByCustomerUserIdAndUpdatedAtAfterAndDeletedAtIsNull(@Param("userId") UUID userId,
                                                                         @Param("since") Instant since,
                                                                         Pageable pageable);

    @Query("""
        SELECT s FROM Site s
        WHERE s.customer.user.id = :userId
          AND s.deletedAt > :since
    """)
    List<Site> findAllByCustomerUserIdAndDeletedAtAfter(@Param("userId") UUID userId,
                                                       @Param("since") Instant since);

    @Query("""
        SELECT s FROM Site s
        WHERE s.customer.user.id = :userId
          AND s.deletedAt IS NULL
          AND (:customerId IS NULL OR s.customer.id = :customerId)
    """)
    Page<Site> findAllByCustomerUserIdAndOptionalCustomerId(@Param("userId") UUID userId,
                                                           @Param("customerId") UUID customerId,
                                                           Pageable pageable);

    @Query("""
        SELECT s FROM Site s
        WHERE s.customer.user.id = :userId
          AND s.deletedAt IS NULL
          AND (:customerId IS NULL OR s.customer.id = :customerId)
          AND (
              LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(s.addressLine1) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(COALESCE(s.city, '')) LIKE LOWER(CONCAT('%', :search, '%'))
          )
    """)
    Page<Site> searchSites(@Param("userId") UUID userId,
                           @Param("customerId") UUID customerId,
                           @Param("search") String search,
                           Pageable pageable);

    @Query("""
        SELECT s FROM Site s
        JOIN FETCH s.customer c
        WHERE s.id = :id
    """)
    Optional<Site> findByIdWithCustomer(@Param("id") UUID id);
}

package com.fieldnotes.customer.repository;

import com.fieldnotes.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(UUID userId, Instant updatedAt, Pageable pageable);

    List<Customer> findAllByUserIdAndDeletedAtAfter(UUID userId, Instant deletedAt);

    Optional<Customer> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<Customer> findByIdAndUserId(UUID id, UUID userId);

    Page<Customer> findAllByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.user.id = :userId
          AND c.deletedAt IS NULL
          AND (
              LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(COALESCE(c.contactEmail, '')) LIKE LOWER(CONCAT('%', :search, '%'))
          )
    """)
    Page<Customer> searchCustomers(@Param("userId") UUID userId,
                                   @Param("search") String search,
                                   Pageable pageable);
}

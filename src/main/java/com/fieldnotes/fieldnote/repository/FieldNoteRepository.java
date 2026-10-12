package com.fieldnotes.fieldnote.repository;

import com.fieldnotes.fieldnote.entity.FieldNote;
import com.fieldnotes.fieldnote.entity.NoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldNoteRepository extends JpaRepository<FieldNote, UUID> {

    List<FieldNote> findAllBySiteIdAndDeletedAtIsNull(UUID siteId);

    @Query("""
        SELECT f FROM FieldNote f
        JOIN FETCH f.site s
        JOIN FETCH s.customer c
        WHERE c.user.id = :userId
          AND f.updatedAt > :since
          AND f.deletedAt IS NULL
        ORDER BY f.updatedAt ASC
    """)
    List<FieldNote> findAllBySiteCustomerUserIdAndUpdatedAtAfterAndDeletedAtIsNull(@Param("userId") UUID userId,
                                                                                   @Param("since") Instant since,
                                                                                   Pageable pageable);

    @Query("""
        SELECT f FROM FieldNote f
        JOIN f.site s
        JOIN s.customer c
        WHERE c.user.id = :userId
          AND f.deletedAt > :since
    """)
    List<FieldNote> findAllBySiteCustomerUserIdAndDeletedAtAfter(@Param("userId") UUID userId,
                                                                 @Param("since") Instant since);

    @Query(value = """
        SELECT f FROM FieldNote f
        JOIN f.site s
        JOIN s.customer c
        WHERE c.user.id = :userId
          AND f.deletedAt IS NULL
          AND (:siteId IS NULL OR s.id = :siteId)
          AND (:customerId IS NULL OR c.id = :customerId)
          AND (:status IS NULL OR f.status = :status)
          AND (
              :search IS NULL
              OR LOWER(f.title) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(f.description) LIKE LOWER(CONCAT('%', :search, '%'))
          )
    """, countQuery = """
        SELECT count(f) FROM FieldNote f
        JOIN f.site s
        JOIN s.customer c
        WHERE c.user.id = :userId
          AND f.deletedAt IS NULL
          AND (:siteId IS NULL OR s.id = :siteId)
          AND (:customerId IS NULL OR c.id = :customerId)
          AND (:status IS NULL OR f.status = :status)
          AND (
              :search IS NULL
              OR LOWER(f.title) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(f.description) LIKE LOWER(CONCAT('%', :search, '%'))
          )
    """)
    Page<FieldNote> findFieldNotes(@Param("userId") UUID userId,
                                   @Param("siteId") UUID siteId,
                                   @Param("customerId") UUID customerId,
                                   @Param("status") NoteStatus status,
                                   @Param("search") String search,
                                   Pageable pageable);

    @Query("""
        SELECT f FROM FieldNote f
        JOIN FETCH f.site s
        JOIN FETCH s.customer c
        WHERE f.id = :id
    """)
    Optional<FieldNote> findByIdWithSiteAndCustomer(@Param("id") UUID id);
}

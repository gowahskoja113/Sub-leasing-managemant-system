package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.PropertyViewingLead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PropertyViewingLeadRepository extends JpaRepository<PropertyViewingLead, Long>,
        JpaSpecificationExecutor<PropertyViewingLead> {

    @Query("""
            SELECT DISTINCT l FROM PropertyViewingLead l
            LEFT JOIN FETCH l.interestedProperties ip
            LEFT JOIN FETCH ip.property
            LEFT JOIN FETCH ip.room
            WHERE l.id = :id
            """)
    Optional<PropertyViewingLead> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "interestedProperties",
            "interestedProperties.property",
            "interestedProperties.room"
    })
    @Query("""
            SELECT l FROM PropertyViewingLead l
            WHERE l.linkedUserId = :userId
               OR l.customerPhone IN :phones
            ORDER BY l.createdAt DESC
            """)
    Page<PropertyViewingLead> findForCustomer(
            @Param("userId") UUID userId,
            @Param("phones") java.util.List<String> phones,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT l FROM PropertyViewingLead l
            LEFT JOIN FETCH l.interestedProperties ip
            LEFT JOIN FETCH ip.property
            LEFT JOIN FETCH ip.room
            WHERE l.id = :id
              AND (l.linkedUserId = :userId OR l.customerPhone IN :phones)
            """)
    Optional<PropertyViewingLead> findForCustomerById(
            @Param("id") Long id,
            @Param("userId") UUID userId,
            @Param("phones") java.util.List<String> phones);
}

package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantContractRepository extends JpaRepository<TenantContract, Long> {

    // Quy tắc 1-HĐ-active: kiểm tra phòng đã có hợp đồng đang hiệu lực chưa
    boolean existsByRoomIdAndStatus(Long roomId, ContractStatus status);

    // Quy tắc 1-HĐ-active cho thuê nguyên căn (room == null)
    boolean existsByPropertyIdAndRoomIsNullAndStatus(Long propertyId, ContractStatus status);

    // Kiểm tra HĐ chồng lấn khoảng [moveInDate, endDate] cho phòng cụ thể
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM TenantContract c
            WHERE c.room.id = :roomId
              AND c.status <> com.sep490.slms2026.enums.ContractStatus.TERMINATED
              AND c.moveInDate < :newEnd
              AND (c.endDate IS NULL OR c.endDate > :newStart)
            """)
    boolean existsOverlappingContractByRoom(@Param("roomId") Long roomId,
                                            @Param("newStart") LocalDate newStart,
                                            @Param("newEnd") LocalDate newEnd);

    // Kiểm tra HĐ chồng lấn khoảng [moveInDate, endDate] cho thuê nguyên căn (room IS NULL)
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM TenantContract c
            WHERE c.property.id = :propertyId
              AND c.room IS NULL
              AND c.status <> com.sep490.slms2026.enums.ContractStatus.TERMINATED
              AND c.moveInDate < :newEnd
              AND (c.endDate IS NULL OR c.endDate > :newStart)
            """)
    boolean existsOverlappingContractByProperty(@Param("propertyId") Long propertyId,
                                                @Param("newStart") LocalDate newStart,
                                                @Param("newEnd") LocalDate newEnd);

    // Các HĐ nguyên căn đang hiệu lực (room == null) — để biết nhà nào đã có khách
    List<TenantContract> findByRoomIsNullAndStatus(ContractStatus status);

    List<TenantContract> findByPropertyId(Long propertyId);

    List<TenantContract> findByTenantId(UUID tenantUserId);

    List<TenantContract> findByStatus(ContractStatus status);

    Page<TenantContract> findByStatus(ContractStatus status, Pageable pageable);

    Optional<TenantContract> findByPayosOrderCode(Long payosOrderCode);


    @Query("""
            SELECT COALESCE(SUM(c.rentAmount), 0)
            FROM TenantContract c
            WHERE c.property.id = :propertyId
              AND c.status = com.sep490.slms2026.enums.ContractStatus.ACTIVE
              AND c.paymentStatus = com.sep490.slms2026.enums.PaymentStatus.PAID
              AND c.paidAt IS NOT NULL
              AND c.paidAt >= :monthStart
              AND c.paidAt < :monthEnd
            """)
    BigDecimal sumPaidRentByPropertyAndMonth(
            @Param("propertyId") Long propertyId,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("monthEnd") LocalDateTime monthEnd);

    @Query("""
            SELECT COUNT(DISTINCT c.room.id)
            FROM TenantContract c
            WHERE c.property.id = :propertyId
              AND c.room IS NOT NULL
              AND c.status = com.sep490.slms2026.enums.ContractStatus.ACTIVE
              AND c.moveInDate <= :asOf
              AND (c.endDate IS NULL OR c.endDate >= :asOf)
            """)
    long countOccupiedRooms(@Param("propertyId") Long propertyId, @Param("asOf") LocalDate asOf);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM TenantContract c
            WHERE c.property.id = :propertyId
              AND c.room IS NULL
              AND c.status = com.sep490.slms2026.enums.ContractStatus.ACTIVE
              AND c.moveInDate <= :asOf
              AND (c.endDate IS NULL OR c.endDate >= :asOf)
            """)
    boolean hasActiveWholeHouseTenant(
            @Param("propertyId") Long propertyId, @Param("asOf") LocalDate asOf);

}

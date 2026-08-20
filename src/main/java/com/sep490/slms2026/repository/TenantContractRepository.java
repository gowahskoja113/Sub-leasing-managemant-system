package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.sep490.slms2026.entity.User;
import java.util.UUID;

@Repository
public interface TenantContractRepository extends JpaRepository<TenantContract, Long> {

    @Modifying
    @Query("UPDATE TenantContract c SET c.assignedManager = :manager WHERE c.property.zone.id = :zoneId AND c.status <> com.sep490.slms2026.enums.ContractStatus.TERMINATED")
    int updateAssignedManagerByZoneId(@Param("manager") User manager, @Param("zoneId") UUID zoneId);

    @Modifying
    @Query("UPDATE TenantContract c SET c.assignedManager = null WHERE c.property.zone.id = :zoneId AND c.status <> com.sep490.slms2026.enums.ContractStatus.TERMINATED")
    int removeAssignedManagerByZoneId(@Param("zoneId") UUID zoneId);

    /** HĐ chưa chấm dứt trong khu vực — dùng khi đổi/gỡ quản lý để thông báo khách. */
    @Query("""
            SELECT c FROM TenantContract c
            JOIN FETCH c.property p
            LEFT JOIN FETCH c.room
            LEFT JOIN FETCH c.tenant t
            LEFT JOIN FETCH t.user
            WHERE p.zone.id = :zoneId
              AND c.status <> com.sep490.slms2026.enums.ContractStatus.TERMINATED
            """)
    List<TenantContract> findActiveAndPendingByZoneId(@Param("zoneId") UUID zoneId);

    // Quy tắc 1-HĐ-active theo ĐƠN VỊ CHO THUÊ (phòng / nguyên căn) — không giới hạn theo SĐT/tenant
    boolean existsByRoomIdAndStatus(Long roomId, ContractStatus status);

    // Quy tắc 1-HĐ-active cho thuê nguyên căn (room == null) — 1 property chỉ 1 HĐ nguyên căn ACTIVE
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

    // Cascade đổi quản lý: lấy HĐ chưa kết thúc của nhà để gán lại assignedManager
    List<TenantContract> findByPropertyIdAndStatusIn(Long propertyId, java.util.Collection<ContractStatus> statuses);

    // Auto-cancel no-show: HĐ nháp/chờ có moveInDate đã quá hạn
    List<TenantContract> findByStatusInAndMoveInDateBefore(
            java.util.Collection<ContractStatus> statuses, LocalDate moveInDate);

    /** Cron nhắc đón khách: DRAFT/PENDING có ngày đón (expectedReceptionDate ?? moveInDate) trùng mốc. */
    @Query("""
            SELECT c FROM TenantContract c
            JOIN FETCH c.property p
            LEFT JOIN FETCH c.room
            LEFT JOIN FETCH c.tenant t
            LEFT JOIN FETCH t.user
            LEFT JOIN FETCH c.assignedManager
            WHERE c.status IN :statuses
              AND COALESCE(c.expectedReceptionDate, c.moveInDate) IN :dates
            """)
    List<TenantContract> findPendingReceptionOnDates(
            @Param("statuses") java.util.Collection<ContractStatus> statuses,
            @Param("dates") java.util.Collection<LocalDate> dates);

    /** Backfill: HĐ chưa kết thúc còn thiếu assignedManager */
    @Query("""
            SELECT c FROM TenantContract c
            JOIN FETCH c.property p
            LEFT JOIN FETCH c.assignedManager
            WHERE c.assignedManager IS NULL
              AND p.operationManagerId IS NOT NULL
              AND c.status IN :statuses
            """)
    List<TenantContract> findMissingAssignedManager(
            @Param("statuses") java.util.Collection<ContractStatus> statuses);

    List<TenantContract> findByTenantId(UUID tenantUserId);

    List<TenantContract> findByStatus(ContractStatus status);

    @Query("""
            SELECT c FROM TenantContract c
            JOIN FETCH c.property p
            LEFT JOIN FETCH c.room
            LEFT JOIN FETCH c.tenant t
            LEFT JOIN FETCH t.user
            WHERE c.status = :status
            """)
    List<TenantContract> findByStatusWithPropertyAndTenant(@Param("status") ContractStatus status);

    @Query("""
            SELECT c FROM TenantContract c
            JOIN FETCH c.property p
            LEFT JOIN FETCH c.room
            LEFT JOIN FETCH c.tenant t
            LEFT JOIN FETCH t.user
            WHERE c.status = :status
              AND p.operationManagerId = :managerId
            """)
    List<TenantContract> findActiveByOperationManagerId(
            @Param("status") ContractStatus status,
            @Param("managerId") UUID managerId);

    Page<TenantContract> findByStatus(ContractStatus status, Pageable pageable);

    @Query("""
            SELECT c FROM TenantContract c 
            JOIN FETCH c.property p 
            LEFT JOIN FETCH c.room
            LEFT JOIN FETCH c.tenant t
            LEFT JOIN FETCH t.user
            WHERE (p.operationManagerId = :managerUserId OR p.operationManagerId = :managerUserId)
              AND (c.priceApprovalStatus IN :statuses 
                   OR c.status IN (com.sep490.slms2026.enums.ContractStatus.PENDING, com.sep490.slms2026.enums.ContractStatus.DRAFT))
            """)
    List<TenantContract> findManagedContractsByApprovalStatuses(
            @Param("managerUserId") UUID managerUserId, 
            @Param("statuses") List<com.sep490.slms2026.enums.PriceApprovalStatus> statuses);

    @Query("""
            SELECT c FROM TenantContract c 
            JOIN FETCH c.property p 
            LEFT JOIN FETCH c.room
            LEFT JOIN FETCH c.tenant t
            LEFT JOIN FETCH t.user
            WHERE (p.operationManagerId = :managerUserId OR p.operationManagerId = :managerUserId)
              AND c.priceApprovalStatus = :status
            """)
    List<TenantContract> findManagedContractsByApprovalStatus(
            @Param("managerUserId") UUID managerUserId, 
            @Param("status") com.sep490.slms2026.enums.PriceApprovalStatus status);

    @Query("""
            SELECT c FROM TenantContract c 
            JOIN FETCH c.property p 
            LEFT JOIN FETCH c.room
            LEFT JOIN FETCH c.tenant t
            LEFT JOIN FETCH t.user
            WHERE (p.operationManagerId = :managerUserId OR p.operationManagerId = :managerUserId)
              AND c.status = :status
            """)
    List<TenantContract> findManagedContractsByStatus(
            @Param("managerUserId") UUID managerUserId, 
            @Param("status") ContractStatus status);

    Page<TenantContract> findByPriceApprovalStatus(com.sep490.slms2026.enums.PriceApprovalStatus status, Pageable pageable);

    Optional<TenantContract> findByPayosOrderCode(Long payosOrderCode);

    boolean existsByContractCode(String contractCode);


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
            """)
    long countActiveRoomContracts(@Param("propertyId") Long propertyId);

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

    @Query("""
            SELECT c FROM TenantContract c
            JOIN FETCH c.tenant t
            JOIN FETCH t.user
            LEFT JOIN FETCH c.room
            WHERE c.property.id = :propertyId
              AND c.status = com.sep490.slms2026.enums.ContractStatus.ACTIVE
            """)
    List<TenantContract> findActiveWithTenantByPropertyId(@Param("propertyId") Long propertyId);

    Optional<TenantContract> findByRoomIdAndStatus(Long roomId, ContractStatus status);

    Optional<TenantContract> findByPropertyIdAndRoomIsNullAndStatus(Long propertyId, ContractStatus status);

    @Query("""
            SELECT c FROM TenantContract c
            WHERE (:propertyId IS NULL OR c.property.id = :propertyId)
              AND (:contractStatus IS NULL OR c.status = :contractStatus)
              AND (:priceApprovalStatus IS NULL OR c.priceApprovalStatus = :priceApprovalStatus)
            """)
    Page<TenantContract> findHostContracts(
            @Param("propertyId") Long propertyId,
            @Param("contractStatus") ContractStatus contractStatus,
            @Param("priceApprovalStatus") com.sep490.slms2026.enums.PriceApprovalStatus priceApprovalStatus,
            Pageable pageable);

    @Query("SELECT c FROM TenantContract c " +
           "WHERE (:status IS NULL OR c.paymentStatus = :status) " +
           "ORDER BY COALESCE(c.paidAt, c.depositCashManagerConfirmedAt) DESC")
    Page<TenantContract> findAdminDeposits(@Param("status") com.sep490.slms2026.enums.PaymentStatus status, Pageable pageable);

    @Query("SELECT c FROM TenantContract c " +
           "JOIN c.property p " +
           "WHERE p.operationManagerId = :managerUserId " +
           "AND (:status IS NULL OR c.paymentStatus = :status) " +
           "ORDER BY COALESCE(c.paidAt, c.depositCashManagerConfirmedAt) DESC")
    Page<TenantContract> findManagerDeposits(@Param("managerUserId") UUID managerUserId, 
                                             @Param("status") com.sep490.slms2026.enums.PaymentStatus status, 
                                             Pageable pageable);

    @Query("SELECT MAX(c.id) FROM TenantContract c")
    Long getMaxId();

    Optional<TenantContract> findFirstByContractCodeStartingWithOrderByContractCodeDesc(String prefix);
}


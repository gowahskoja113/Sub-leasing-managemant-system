package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Hợp đồng thuê giữa khách thuê (Tenant) và một phòng/căn nhà.
 * Mô phỏng pattern của {@link InboundContract}. Quy tắc: mỗi phòng chỉ có 1 HĐ ACTIVE tại một thời điểm
 * (đảm bảo ở tầng service, không phải ràng buộc DB, để giữ được HĐ cũ TERMINATED/EXPIRED cùng phòng).
 */
@Entity
@Table(name = "tenant_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantContract implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_user_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    // Null khi thuê nguyên căn (whole house), có giá trị khi thuê theo phòng
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "contract_code", unique = true, nullable = false)
    private String contractCode;

    @Column(name = "rent_amount", nullable = false)
    private BigDecimal rentAmount;

    // Tiền cọc theo dõi riêng theo quy tắc nghiệp vụ
    @Column(name = "deposit", nullable = false)
    private BigDecimal deposit;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Nullable: cho phép hợp đồng không thời hạn cố định
    @Column(name = "end_date")
    private LocalDate endDate;

    // Snapshot tình trạng thiết bị bàn giao (rút gọn; chi tiết theo từng item sẽ làm sau)
    @Column(name = "equipment_snapshot", columnDefinition = "TEXT")
    private String equipmentSnapshot;

    // Ảnh hiện trạng phòng — DEFERRED (upload ảnh sẽ nối sau)
    @Column(name = "room_condition_url")
    private String roomConditionUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.ACTIVE;
}

package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @JoinColumn(name = "tenant_user_id", nullable = true)
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

    // Số tháng tiền cọc (1 hoặc 2 tháng giá thuê) — phục vụ hiển thị/đối chiếu
    @Column(name = "deposit_months")
    private Integer depositMonths;

    // Chỉ số điện/nước đầu kỳ (chốt lúc onboarding)
    @Column(name = "initial_electric_reading")
    private BigDecimal initialElectricReading;

    @Column(name = "initial_water_reading")
    private BigDecimal initialWaterReading;

    // Ảnh đồng hồ điện/nước đầu kỳ (Cloudinary URL) — lưu trong hợp đồng
    @Column(name = "electric_meter_image_url")
    private String electricMeterImageUrl;

    @Column(name = "water_meter_image_url")
    private String waterMeterImageUrl;

    /** Thời điểm chụp/ghi nhận ảnh đồng hồ điện (bằng chứng). */
    @Column(name = "electric_meter_captured_at")
    private LocalDateTime electricMeterCapturedAt;

    /** Thời điểm chụp/ghi nhận ảnh đồng hồ nước (bằng chứng). */
    @Column(name = "water_meter_captured_at")
    private LocalDateTime waterMeterCapturedAt;

    // Ảnh hiện trạng phòng/nhà kèm thời điểm chụp
    @ElementCollection
    @CollectionTable(name = "tenant_contract_condition_photos",
            joinColumns = @JoinColumn(name = "tenant_contract_id"))
    @Builder.Default
    private List<ContractEvidencePhoto> roomConditionPhotos = new ArrayList<>();

    @Column(name = "room_condition_note", columnDefinition = "TEXT")
    private String roomConditionNote;

    // Thanh toán cọc qua PayOS.
    // Cố ý KHÔNG đặt nullable=false: để ddl-auto=update có thể thêm cột vào bảng đã có dữ liệu
    // (Postgres không cho thêm cột NOT NULL không default). App luôn set PENDING qua @Builder.Default.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payos_order_code")
    private Long payosOrderCode;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Khách xác nhận đã trả cọc tiền mặt. */
    @Column(name = "deposit_cash_tenant_confirmed_at")
    private LocalDateTime depositCashTenantConfirmedAt;

    /** Manager xác nhận đã nhận cọc tiền mặt. */
    @Column(name = "deposit_cash_manager_confirmed_at")
    private LocalDateTime depositCashManagerConfirmedAt;

    /** URL public file hợp đồng DOCX đã xuất (lưu local giống ảnh property). */
    @Column(name = "document_url")
    private String documentUrl;

    @Column(name = "document_generated_at")
    private LocalDateTime documentGeneratedAt;

    // Thành viên ở cùng
    @OneToMany(mappedBy = "tenantContract", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<HouseholdMember> householdMembers = new ArrayList<>();

    /** Thiết bị khách nhận bàn giao (subset theo deal từng HĐ). */
    @OneToMany(mappedBy = "tenantContract", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TenantContractEquipment> selectedEquipments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_approval_status")
    private com.sep490.slms2026.enums.PriceApprovalStatus priceApprovalStatus;

    @Column(name = "price_reject_reason", columnDefinition = "TEXT")
    private String priceRejectReason;

    @Column(name = "handover_acknowledged_at")
    private LocalDateTime handoverAcknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_manager_id")
    private User assignedManager;

    @Column(name = "draft_contract_file_url", length = 512)
    private String draftContractFileUrl;

    @Column(name = "expected_reception_date")
    private LocalDate expectedReceptionDate;

    // Các field lưu tạm khi hợp đồng ở trạng thái DRAFT (chưa tạo account)
    @Column(name = "draft_tenant_name")
    private String draftTenantName;

    @Column(name = "draft_tenant_phone")
    private String draftTenantPhone;

    @Column(name = "draft_tenant_cccd")
    private String draftTenantCccd;

    @Column(name = "draft_tenant_dob")
    private LocalDate draftTenantDob;

    @Column(name = "draft_tenant_cccd_issue_date")
    private LocalDate draftTenantCccdIssueDate;

    @Column(name = "draft_tenant_cccd_issue_place")
    private String draftTenantCccdIssuePlace;

    @Column(name = "draft_tenant_address")
    private String draftTenantAddress;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_type")
    private com.sep490.slms2026.enums.ContractTerminationType terminationType;

    @Column(name = "termination_reason", columnDefinition = "TEXT")
    private String terminationReason;

    @Column(name = "termination_note", columnDefinition = "TEXT")
    private String terminationNote;
}

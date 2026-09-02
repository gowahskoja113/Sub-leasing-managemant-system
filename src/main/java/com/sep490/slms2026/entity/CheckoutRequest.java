package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.CheckoutRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkout_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_user_id", nullable = false)
    private UUID tenantUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_contract_id", nullable = false)
    private TenantContract tenantContract;

    @Column(name = "expected_move_out_date", nullable = false)
    private LocalDate expectedMoveOutDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CheckoutRequestStatus status = CheckoutRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 32)
    private com.sep490.slms2026.enums.CheckoutOrigin origin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "manager_note", columnDefinition = "TEXT")
    private String managerNote;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "dispute_count")
    @Builder.Default
    private Integer disputeCount = 0;

    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    @ElementCollection
    @CollectionTable(name = "checkout_request_dispute_photos", joinColumns = @JoinColumn(name = "checkout_request_id"))
    @Column(name = "photo_url")
    @Builder.Default
    private java.util.List<String> disputePhotos = new java.util.ArrayList<>();

    @Column(name = "disputed_at")
    private LocalDateTime disputedAt;

    @Column(name = "refund_bank_name")
    private String refundBankName;

    @Column(name = "refund_bank_account")
    private String refundBankAccount;

    @Column(name = "refund_account_holder")
    private String refundAccountHolder;

    @Column(name = "refund_due_date")
    private LocalDate refundDueDate;
}

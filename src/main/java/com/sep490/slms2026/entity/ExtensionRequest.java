package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.ExtensionRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "extension_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_contract_id", nullable = false)
    private TenantContract tenantContract;

    @Column(name = "tenant_user_id", nullable = false)
    private UUID tenantUserId;

    @Column(nullable = false)
    private Integer months;

    @Column(name = "new_end_date", nullable = false)
    private LocalDate newEndDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExtensionRequestStatus status = ExtensionRequestStatus.PENDING;

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
}

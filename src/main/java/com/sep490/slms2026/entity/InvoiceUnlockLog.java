package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.InvoiceUnlockPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_unlock_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceUnlockLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvoiceUnlockPurpose purpose;

    @Column(name = "unlocked_by_admin", nullable = false)
    private UUID unlockedByAdmin;

    @Column(name = "passcode_id")
    private Long passcodeId;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "payment_result")
    private String paymentResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

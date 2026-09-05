package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_unlock_fail_counters")
@IdClass(InvoiceUnlockFailCounterId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceUnlockFailCounter implements Serializable {

    @Id
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Id
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "fail_count", nullable = false)
    @Builder.Default
    private int failCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}

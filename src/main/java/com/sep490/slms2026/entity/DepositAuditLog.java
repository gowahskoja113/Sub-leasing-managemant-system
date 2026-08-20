package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deposit_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositAuditLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "at", nullable = false)
    private LocalDateTime at;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @PrePersist
    protected void onCreate() {
        if (at == null) {
            at = LocalDateTime.now();
        }
    }
}

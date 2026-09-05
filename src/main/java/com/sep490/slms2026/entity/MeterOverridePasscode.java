package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mã một lần do admin gen — manager nhập để xin overrideToken.
 * Dùng xong (usedAt) hoặc hết hạn (expiresAt) thì chết.
 */
@Entity
@Table(name = "meter_override_passcodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterOverridePasscode implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã số ngắn (vd. 6 chữ số) — dễ đọc cho manager qua điện thoại. */
    @Column(nullable = false, length = 16)
    private String code;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_by")
    private UUID usedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}

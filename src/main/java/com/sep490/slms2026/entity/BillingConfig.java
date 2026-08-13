package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Số ngày bắt đầu nhắc trước mốc đóng tiền (mentor: −3). */
    @Column(name = "reminder_lead_days", nullable = false)
    private int reminderLeadDays;

    /** Số ngày sau mốc mới thành hạn chót / OVERDUE (mentor: +2). */
    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    /** Nhắc manager chụp công tơ trước mốc ghi điện (mentor: 1 ngày). */
    @Column(name = "meter_reminder_lead_days", nullable = false)
    private int meterReminderLeadDays;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static BillingConfig defaults() {
        return BillingConfig.builder()
                .id(SINGLETON_ID)
                .reminderLeadDays(3)
                .graceDays(2)
                .meterReminderLeadDays(1)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}

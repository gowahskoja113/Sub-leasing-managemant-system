package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.MaintenanceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "Maintenance_History")
public class MaintenanceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Thuộc request nào
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private MaintenanceRequest maintenanceRequest;

    // Người thực hiện thay đổi (manager/staff)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    // Trạng thái mới được cập nhật
    @Enumerated(EnumType.STRING)
    @Column(name = "status_changed_to", nullable = false)
    private MaintenanceStatus statusChangedTo;

    // Mô tả hành động đã làm (ví dụ: "Đã thay mới bóng đèn phòng 101")
    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    // Chi phí phát sinh ở bước này (nếu có)
    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        this.performedAt = LocalDateTime.now();
    }
}
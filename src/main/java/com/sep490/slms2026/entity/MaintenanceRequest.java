package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import jakarta.persistence.*; // Đã sửa import chuẩn của JPA
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "maintenance_requests")
@Getter
@Setter
public class MaintenanceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String requestCode;

    // Các trường UUID này có thể đổi thành @ManyToOne khi bạn hoàn thiện các Entity liên quan
    private UUID tenantId;
    private UUID roomId;
    private UUID propertyId;
    private UUID assignedManagerId;

    // QUAN HỆ MỚI: Yêu cầu này thuộc về thiết bị nào
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    private MaintenanceCategory category;

    @Enumerated(EnumType.STRING)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status = MaintenanceStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime scheduledDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // QUAN HỆ MỚI: 1 Yêu cầu có nhiều hình ảnh đính kèm
    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaintenanceImage> images = new ArrayList<>();

    // QUAN HỆ MỚI: 1 Yêu cầu có nhiều lần đổi trạng thái (Từ Chờ xử lý -> Đang sửa -> Hoàn tất)
    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaintenanceHistory> statusHistories = new ArrayList<>();

    // QUAN HỆ MỚI: 1 Yêu cầu chỉ có 1 kết quả chốt sửa chữa (hóa đơn, chi phí)
    @OneToOne(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private MaintenanceResolution resolution;
}
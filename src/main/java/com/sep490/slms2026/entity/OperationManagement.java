package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "Operation_Management")
public class OperationManagement {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "manager_zones", // Tên bảng trung gian đồng bộ với file md thiết kế
            joinColumns = @JoinColumn(name = "manager_id", referencedColumnName = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "zone_id")
    )
    private List<Zone> zones = new ArrayList<>();
}
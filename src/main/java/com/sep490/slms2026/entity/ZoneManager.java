package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "zone_managers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneManager {

    @Id
    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

}

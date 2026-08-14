package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "zone_manager_handovers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneManagerHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "from_manager_id")
    private UUID fromManagerId;

    @Column(name = "to_manager_id")
    private UUID toManagerId;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "affected_properties")
    private Integer affectedProperties;

    @Column(name = "affected_contracts")
    private Integer affectedContracts;

}

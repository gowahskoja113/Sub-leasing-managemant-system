package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "maintenance_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id", nullable = false)
    private MaintenanceRequest maintenanceRequest;

    @Column(nullable = false)
    private String imageUrl;
}
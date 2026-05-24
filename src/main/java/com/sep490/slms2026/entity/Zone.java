package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "Zone")
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Một Zone có nhiều Property
    @OneToMany(mappedBy = "zone", fetch = FetchType.LAZY)
    private List<Property> properties = new ArrayList<>();

    // Quan hệ Many-to-Many đảo ngược từ OperationManagement
    @ManyToMany(mappedBy = "zones", fetch = FetchType.LAZY)
    private List<OperationManagement> managers = new ArrayList<>();
}
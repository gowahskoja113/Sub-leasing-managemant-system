package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "checkout_inspections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutInspection implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_request_id", nullable = false)
    private CheckoutRequest checkoutRequest;

    @Column(name = "room_condition_note", columnDefinition = "TEXT")
    private String roomConditionNote;

    @Column(name = "electricity_final_reading")
    private Integer electricityFinalReading;

    @Column(name = "water_final_reading")
    private Integer waterFinalReading;

    @ElementCollection
    @CollectionTable(name = "checkout_inspection_photos", joinColumns = @JoinColumn(name = "inspection_id"))
    @Column(name = "photo_url")
    @Builder.Default
    private List<String> photos = new ArrayList<>();

    @OneToMany(mappedBy = "checkoutInspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheckoutDamageItem> damages = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

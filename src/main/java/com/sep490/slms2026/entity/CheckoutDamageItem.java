package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "checkout_damage_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutDamageItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_inspection_id", nullable = false)
    private CheckoutInspection checkoutInspection;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ElementCollection
    @CollectionTable(name = "checkout_damage_item_photos", joinColumns = @JoinColumn(name = "damage_item_id"))
    @Column(name = "photo_url")
    @Builder.Default
    private List<String> photos = new ArrayList<>();
}

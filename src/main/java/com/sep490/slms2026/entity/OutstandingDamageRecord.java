package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "outstanding_damage_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutstandingDamageRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "maintenance_request_id", nullable = false)
    private Long maintenanceRequestId;

    @Column(name = "tenant_contract_id", nullable = false)
    private Long tenantContractId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(nullable = false)
    private String label;

    @Column(name = "estimated_amount", nullable = false)
    private BigDecimal estimatedAmount;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "resolved_at_checkout", nullable = false)
    @Builder.Default
    private boolean resolvedAtCheckout = false;

    @Column(name = "checkout_damage_item_id")
    private Long checkoutDamageItemId;

    @ElementCollection
    @CollectionTable(name = "outstanding_damage_photos", joinColumns = @JoinColumn(name = "record_id"))
    @Column(name = "photo_url")
    @Builder.Default
    private List<String> photos = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

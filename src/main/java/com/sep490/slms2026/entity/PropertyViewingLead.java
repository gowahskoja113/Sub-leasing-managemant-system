package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.ViewingLeadStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "property_viewing_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyViewingLead implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ViewingLeadStatus status = ViewingLeadStatus.NEW;

    @Column(name = "assigned_manager_id")
    private UUID assignedManagerId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    @Column(name = "preferred_viewing_at")
    private LocalDateTime preferredViewingAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ViewingLeadProperty> interestedProperties = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

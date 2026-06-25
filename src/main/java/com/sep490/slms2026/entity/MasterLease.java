package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.MasterLeaseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "master_leases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterLease implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    private String ownerName;
    private String ownerPhone;

    private BigDecimal monthlyRent;
    private BigDecimal deposit;

    private Integer paymentDay;

    private LocalDate startDate;
    private LocalDate endDate;

    private Double escalationPct;

    @Enumerated(EnumType.STRING)
    private MasterLeaseStatus status = MasterLeaseStatus.ACTIVE;

    private LocalDateTime createdAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

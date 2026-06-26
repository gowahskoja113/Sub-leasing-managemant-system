package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.HostExpenseCategory;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "host_expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HostExpense implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private HostExpenseCategory category;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Tháng ghi nhận chi phí (YYYY-MM). */
    @Column(nullable = false, length = 7)
    private String month;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

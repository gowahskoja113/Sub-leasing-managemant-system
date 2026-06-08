package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "renovations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Renovation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // Null khi mới tạo — cập nhật khi có báo giá thực tế
    @Column
    private BigDecimal cost;

    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;
}

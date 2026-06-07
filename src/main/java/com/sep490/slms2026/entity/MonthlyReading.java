package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

import java.time.LocalDate;

@Entity
@Table(name = "monthly_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "electricity_initial_num")
    private Integer electricityInitialNum; // Số điện đầu vào lúc bàn giao

    @Column(name = "water_initial_num")
    private Integer waterInitialNum; // Số nước đầu vào lúc bàn giao

    @Column(name = "recorded_date")
    private LocalDate recordedDate; // Ngày ghi nhận số đầu kỳ
}
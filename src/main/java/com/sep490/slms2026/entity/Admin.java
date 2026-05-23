package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "Admin")
public class Admin {

    @Id
    @Column(name = "user_id") // Khóa chính của Admin cũng chính là user_id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Báo cho Hibernate biết lấy ID của User làm ID cho Admin
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_at")
    private LocalDateTime startAt;
}
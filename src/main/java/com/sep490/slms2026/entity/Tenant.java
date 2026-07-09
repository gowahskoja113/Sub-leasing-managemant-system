package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Profile của khách thuê (1-1 với User có role ROLE_TENANT), theo cùng pattern với {@link Owner}.
 * CCCD lưu ở đây vì entity User không có cột này.
 */
@Setter
@Getter
@Entity
@Table(name = "Tenant")
public class Tenant {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "cccd")
    private String cccd;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;
}

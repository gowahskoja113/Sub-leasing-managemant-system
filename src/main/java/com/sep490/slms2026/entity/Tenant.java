package com.sep490.slms2026.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "citizen_id_number", unique = true)
    private String citizenIdNumber;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "room_rental_status")
    private String roomRentalStatus;
}
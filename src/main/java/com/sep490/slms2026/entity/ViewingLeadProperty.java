package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.ViewingInterestType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "viewing_lead_properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViewingLeadProperty implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private PropertyViewingLead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false)
    private ViewingInterestType interestType;

    @Column(columnDefinition = "TEXT")
    private String note;
}

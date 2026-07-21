package com.sep490.slms2026.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ảnh bằng chứng onboard (hiện trạng phòng/nhà) kèm thời điểm chụp/ghi nhận.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractEvidencePhoto {

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    /** Thời điểm chụp (FE gửi) hoặc thời điểm BE ghi nhận nếu FE không gửi. */
    @Column(name = "captured_at")
    private LocalDateTime capturedAt;
}

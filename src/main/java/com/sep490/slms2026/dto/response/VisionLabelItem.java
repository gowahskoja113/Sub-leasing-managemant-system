package com.sep490.slms2026.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisionLabelItem {

    /** Nhãn tiếng Anh từ nhà cung cấp vision (vd: "air conditioner"). */
    private String name;

    /** Điểm tin cậy 0..1. */
    private double score;
}

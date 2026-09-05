package com.sep490.slms2026.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisionLabelsResponse {

    private List<VisionLabelItem> labels;
}

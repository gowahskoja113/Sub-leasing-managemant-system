package com.sep490.slms2026.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenovationCategoryResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
}

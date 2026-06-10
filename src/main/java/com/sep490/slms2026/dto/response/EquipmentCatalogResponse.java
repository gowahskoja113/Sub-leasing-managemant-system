package com.sep490.slms2026.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentCatalogResponse {

    private Long id;
    private String name;
    private String description;
}

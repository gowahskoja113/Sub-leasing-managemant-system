package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TenantSortResponse {

    private String zoneName;

    private Long total;
}
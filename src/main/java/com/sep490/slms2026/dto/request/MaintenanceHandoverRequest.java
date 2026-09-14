package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MaintenanceHandoverRequest {
    private List<String> handoverImages;
}

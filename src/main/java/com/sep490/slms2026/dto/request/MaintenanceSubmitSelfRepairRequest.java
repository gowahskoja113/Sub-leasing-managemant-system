package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MaintenanceSubmitSelfRepairRequest {
    private String note;
    private List<String> selfRepairImages;
}

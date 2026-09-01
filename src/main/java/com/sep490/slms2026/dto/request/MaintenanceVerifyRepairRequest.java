package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MaintenanceVerifyRepairRequest {
    private boolean accepted;
    private String note;
    private List<String> verifyImages;
}

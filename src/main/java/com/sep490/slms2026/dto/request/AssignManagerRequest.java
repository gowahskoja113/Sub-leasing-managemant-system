package com.sep490.slms2026.dto.request;

import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssignManagerRequest {
    private UUID assignedManagerId;
    private LocalDate expectedReceptionDate;
}

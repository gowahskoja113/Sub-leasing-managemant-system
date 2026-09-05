package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExtensionNoteRequest {
    @NotBlank(message = "Ghi chú không được để trống")
    private String note;
}

package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MaintenanceCompleteRequest {
    /** Ghi chú sau khi sửa (optional). */
    private String resolutionNote;
    /** URL ảnh AFTER nếu đã upload sẵn; hoặc upload qua POST /photos?type=AFTER. */
    private List<String> afterImages;
}

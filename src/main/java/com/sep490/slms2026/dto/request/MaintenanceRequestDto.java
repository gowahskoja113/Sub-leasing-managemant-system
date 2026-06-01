package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class MaintenanceRequestDto {

    // Có thể null nếu tenant không biết thiết bị cụ thể
    private UUID equipmentId;

    // Tenant quét QR → qrPayload được nhúng sẵn trong link, FE tự truyền lên
    private String qrPayload;

    private String category;       // Loại sự cố: ELECTRICAL, PLUMBING, ...

    private String description;    // Mô tả chi tiết sự cố

    private String priority;       // LOW | NORMAL | HIGH | URGENT (default NORMAL)

    // Ảnh hiện trạng (BEFORE) — danh sách URL đã upload lên storage
    private List<String> photoUrls;
}
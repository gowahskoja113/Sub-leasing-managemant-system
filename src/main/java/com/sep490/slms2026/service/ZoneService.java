package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.ZoneRequest;
import com.sep490.slms2026.dto.response.ZoneResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface ZoneService {
    ZoneResponse createZone(ZoneRequest request);
    Page<ZoneResponse> getAllZones(Pageable pageable);
    ZoneResponse getZoneById(UUID id);
    ZoneResponse updateZone(UUID id, ZoneRequest request);
    void deleteZone(UUID id);

    // Thêm các hàm phân cấp
    List<ZoneResponse> getRootZones();
    List<ZoneResponse> getChildrenZones(UUID parentId);
}
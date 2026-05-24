package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ZoneRequest;
import com.sep490.slms2026.dto.response.ZoneResponse;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.mapper.ZoneMapper;
import com.sep490.slms2026.repository.ZoneRepository;
import com.sep490.slms2026.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZoneServiceImpl implements ZoneService {

    private final ZoneRepository zoneRepository;
    private final ZoneMapper zoneMapper;

    @Override
    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        if (zoneRepository.existsByName(request.getName())) {
            throw new RuntimeException("Tên vùng (Zone name) này đã tồn tại trên hệ thống!");
        }
        Zone zone = zoneMapper.toEntity(request);
        return zoneMapper.toResponse(zoneRepository.save(zone));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ZoneResponse> getAllZones(Pageable pageable) {
        return zoneRepository.findAll(pageable).map(zoneMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneResponse getZoneById(UUID id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Khu Vực (Zone) này!"));
        return zoneMapper.toResponse(zone);
    }

    @Override
    @Transactional
    public ZoneResponse updateZone(UUID id, ZoneRequest request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Khu Vực (Zone) này!"));

        if (zoneRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new RuntimeException("Tên vùng này đã trùng với một khu vực khác!");
        }

        zoneMapper.updateEntityFromRequest(request, zone);
        return zoneMapper.toResponse(zoneRepository.save(zone));
    }

    @Override
    @Transactional
    public void deleteZone(UUID id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Khu Vực (Zone) này!"));

        // Tránh lỗi gãy khóa ngoại dữ liệu: Kiểm tra xem Zone này đã có BĐS nào thuộc về chưa
        if (zone.getProperties() != null && !zone.getProperties().isEmpty()) {
            throw new RuntimeException("Không thể xóa khu vực này vì đang có Bất động sản trực thuộc!");
        }

        zoneRepository.delete(zone);
    }
}
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZoneServiceImpl implements ZoneService {

    private final ZoneRepository zoneRepository;
    private final ZoneMapper zoneMapper;

    @Override
    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        validateZoneHierarchy(request.getLevel(), request.getParentId());

        // Kiểm tra trùng tên trong cùng một cấp vùng cha
        if (request.getParentId() == null) {
            if (zoneRepository.existsByNameAndParentIsNull(request.getName())) {
                throw new RuntimeException("Tên Tỉnh/Thành phố này đã tồn tại trên hệ thống!");
            }
        } else {
            if (zoneRepository.existsByNameAndParentId(request.getName(), request.getParentId())) {
                throw new RuntimeException("Tên khu vực này đã tồn tại trong vùng cha được chọn!");
            }
        }

        Zone zone = zoneMapper.toEntity(request);
        if (request.getParentId() != null) {
            Zone parent = zoneRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Khu Vực cha!"));
            zone.setParent(parent);
        }

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

        validateZoneHierarchy(request.getLevel(), request.getParentId());

        // Kiểm tra trùng tên loại trừ chính nó
        if (request.getParentId() == null) {
            if (zoneRepository.existsByNameAndParentIsNullAndIdNot(request.getName(), id)) {
                throw new RuntimeException("Tên Tỉnh/Thành phố này đã trùng với một khu vực khác!");
            }
            zone.setParent(null);
        } else {
            if (zoneRepository.existsByNameAndParentIdAndIdNot(request.getName(), request.getParentId(), id)) {
                throw new RuntimeException("Tên khu vực này đã trùng với khu vực khác trong cùng vùng cha!");
            }
            Zone parent = zoneRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Khu Vực cha!"));
            zone.setParent(parent);
        }

        zoneMapper.updateEntityFromRequest(request, zone);
        return zoneMapper.toResponse(zoneRepository.save(zone));
    }

    @Override
    @Transactional
    public void deleteZone(UUID id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Khu Vực (Zone) này!"));

        if (zone.getProperties() != null && !zone.getProperties().isEmpty()) {
            throw new RuntimeException("Không thể xóa khu vực này vì đang có Bất động sản trực thuộc!");
        }

        if (zone.getChildren() != null && !zone.getChildren().isEmpty()) {
            throw new RuntimeException("Không thể xóa khu vực này vì đang chứa các khu vực con!");
        }

        zoneRepository.delete(zone);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> getRootZones() {
        return zoneRepository.findAllByLevel(1).stream()
                .map(zoneMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> getChildrenZones(UUID parentId) {
        return zoneRepository.findAllByParentId(parentId).stream()
                .map(zoneMapper::toResponse)
                .collect(Collectors.toList());
    }

    private void validateZoneHierarchy(Integer level, UUID parentId) {
        if (level == 1) {
            if (parentId != null) {
                throw new RuntimeException("Khu vực cấp 1 (Tỉnh/Thành phố) không được phép có vùng cha (parentId phải là null)!");
            }
        } else {
            if (parentId == null) {
                throw new RuntimeException("Khu vực cấp " + level + " bắt buộc phải có một vùng cha (parentId)!");
            }
            Zone parent = zoneRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Vùng cha chỉ định!"));
            if (!parent.getLevel().equals(level - 1)) {
                throw new RuntimeException("Mối quan hệ phân cấp không hợp lệ! Vùng cha phải có cấp độ (level) là: " + (level - 1));
            }
        }
    }
}
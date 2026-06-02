package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.EquipmentAssignRequest;
import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.mapper.EquipmentMapper;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.EquipmentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentServiceImpl implements EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final EquipmentMapper equipmentMapper;

    /**
     * Base URL cho QR payload — trỏ đến màn hình bảo trì.
     * Cấu hình trong application.properties:
     *   app.qr.base-url=https://your-app.com/maintenance
     */
    @Value("${app.qr.base-url:https://slms2026.app/maintenance}")
    private String qrBaseUrl;

    // ──────────────────────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────────────────────

    @Override
    public EquipmentResponse create(EquipmentRequest request) {
        validateAssignmentTarget(request.getRoomId(), request.getPropertyId());

        Equipment equipment = equipmentMapper.toEntity(request);

        // Gán room nếu có roomId
        if (request.getRoomId() != null) {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy phòng với id: " + request.getRoomId()));
            equipment.setRoom(room);
        }
        if (request.getPropertyId() != null) {
            Property property = propertyRepository.findById(request.getPropertyId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy nhà với id: " + request.getPropertyId()));
            equipment.setProperty(property);
        }

        // Nếu chỉ có propertyId (whole-house): để room = null,
        // thông tin property được resolve qua mapper helper khi response.
        // Ta cần lưu propertyId đâu đó — xem note bên dưới (*).
        // Hiện tại entity Equipment chỉ có room; nếu muốn hỗ trợ whole-house
        // assignment chuẩn thì nên thêm @ManyToOne property vào entity.
        // Tạm thời: nếu propertyId được truyền mà không có roomId,
        // ta verify property tồn tại rồi để room = null.
        if (request.getPropertyId() != null && request.getRoomId() == null) {
            verifyPropertyExists(request.getPropertyId());
            // (*) TODO: khi entity Equipment có thêm field `property`,
            // set equipment.setProperty(property) ở đây.
        }

        // Generate QR payload (unique per equipment)
        equipment = equipmentRepository.save(equipment); // lưu trước để có UUID
        String qrPayload = null;
        equipment.setQrPayload(qrPayload);
        // qrCode (ảnh QR base64 hoặc URL ảnh) — để ở phase sau
        // equipment.setQrCode(qrGeneratorService.generate(qrPayload));

        equipment = equipmentRepository.save(equipment);
        return toEnrichedResponse(equipment, request.getPropertyId());
    }

    // ──────────────────────────────────────────────────────────────
    // READ
    // ──────────────────────────────────────────────────────────────

    @Override
    public EquipmentResponse getById(UUID id) {
        Equipment equipment = findById(id);
        return toEnrichedResponse(equipment, null);
    }

    @Override
    public List<EquipmentResponse> getByRoom(UUID roomId) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng: " + roomId));
        return equipmentRepository.findByRoomId(roomId).stream()
                .map(e -> toEnrichedResponse(e, null))
                .collect(Collectors.toList());
    }

    @Override
    public List<EquipmentResponse> getByProperty(UUID propertyId) {
        verifyPropertyExists(propertyId);
        return equipmentRepository.findByPropertyId(propertyId).stream()
                .map(e -> toEnrichedResponse(e, null))
                .collect(Collectors.toList());
    }

    @Override
    public EquipmentResponse getByQrPayload(String qrPayload) {
        Equipment equipment = equipmentRepository
                .findAll().stream()
                .filter(e -> qrPayload.equals(e.getQrPayload()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy thiết bị với QR payload: " + qrPayload));
        return toEnrichedResponse(equipment, null);
    }

    // ──────────────────────────────────────────────────────────────
    // UPDATE
    // ──────────────────────────────────────────────────────────────

    @Override
    public EquipmentResponse update(UUID id, EquipmentRequest request) {
        Equipment equipment = findById(id);
        equipmentMapper.updateEntityFromRequest(request, equipment);
        equipment = equipmentRepository.save(equipment);
        return toEnrichedResponse(equipment, null);
    }

    // ──────────────────────────────────────────────────────────────
    // ASSIGN
    // ──────────────────────────────────────────────────────────────

    @Override
    public EquipmentResponse assign(UUID id, EquipmentAssignRequest assignRequest) {
        validateAssignmentTarget(assignRequest.getRoomId(), assignRequest.getPropertyId());

        Equipment equipment = findById(id);

        if (assignRequest.getRoomId() != null) {
            // Gán vào phòng cụ thể
            Room room = roomRepository.findById(assignRequest.getRoomId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy phòng: " + assignRequest.getRoomId()));
            equipment.setRoom(room);
        } else if (assignRequest.getPropertyId() != null) {
            // Chuyển về nhà nguyên căn (whole-house)
            verifyPropertyExists(assignRequest.getPropertyId());
            equipment.setRoom(null);
            // TODO: set equipment.setProperty(...) khi thêm field vào entity
        } else {
            // Bỏ gán hoàn toàn
            equipment.setRoom(null);
        }

        equipment = equipmentRepository.save(equipment);
        return toEnrichedResponse(equipment, assignRequest.getPropertyId());
    }

    // ──────────────────────────────────────────────────────────────
    // DELETE
    // ──────────────────────────────────────────────────────────────

    @Override
    public void delete(UUID id) {
        Equipment equipment = findById(id);
        equipmentRepository.delete(equipment);
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────────

    private Equipment findById(UUID id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy thiết bị với id: " + id));
    }

    private void verifyPropertyExists(UUID propertyId) {
        propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy bất động sản với id: " + propertyId));
    }

    /**
     * Chỉ được chọn 1 trong 2: roomId hoặc propertyId.
     */
    private void validateAssignmentTarget(UUID roomId, UUID propertyId) {
        if (roomId != null && propertyId != null) {
            throw new IllegalArgumentException(
                    "Chỉ được gán vào phòng (roomId) hoặc nhà nguyên căn (propertyId), không được cả hai.");
        }
    }

    /**
     * Build QR payload dạng deep-link: {baseUrl}/{equipmentId}
     * Ví dụ: https://slms2026.app/maintenance/550e8400-e29b-41d4-a716-446655440000
     */
    private String buildQrPayload(UUID equipmentId) {
        return qrBaseUrl + "/" + equipmentId;
    }

    /**
     * Map sang response và enrich các field property khi equipment là whole-house
     * (room == null nhưng có propertyId từ context).
     */
    private EquipmentResponse toEnrichedResponse(Equipment equipment, UUID fallbackPropertyId) {
        EquipmentResponse response = equipmentMapper.toResponse(equipment);

        // Nếu equipment không có room (whole-house), tự resolve property từ fallback
        if (equipment.getRoom() == null && fallbackPropertyId != null) {
            propertyRepository.findById(fallbackPropertyId).ifPresent(p -> {
                response.setPropertyId(p.getId());
                response.setPropertyTitle(p.getTitle());
                response.setPropertyAddress(p.getAddress());
            });
        }

        return response;
    }
}
package com.sep490.slms2026.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.mapper.EquipmentMapper;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentServiceImpl implements EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final RoomRepository roomRepository;
    private final EquipmentMapper equipmentMapper;

    // Base URL cho QR payload — cấu hình trong application.yml
    // Ví dụ: https://app.slms2026.com/maintenance/scan
    @Value("${app.qr.base-url:https://app.slms2026.com/maintenance/scan}")
    private String qrBaseUrl;

    @Override
    @Transactional
    public EquipmentResponse createEquipment(EquipmentRequest request, UUID managerId) {
        Equipment equipment = equipmentMapper.toEntity(request);
        equipment.setStatus(EquipmentStatus.ACTIVE);

        if (request.getRoomId() != null) {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với ID: " + request.getRoomId()));
            equipment.setRoom(room);
        }

        // Sinh QR payload: base URL + equipmentId (UUID sinh trước)
        UUID equipmentId = UUID.randomUUID();
        String qrPayload = qrBaseUrl + "?equipmentId=" + equipmentId;
        equipment.setQrPayload(qrPayload);

        // Sinh ảnh QR dạng base64
        try {
            String qrBase64 = generateQrBase64(qrPayload);
            equipment.setQrCode(qrBase64);
        } catch (Exception e) {
            // Không fail cả luồng nếu QR sinh lỗi — log và để null
            equipment.setQrCode(null);
        }

        // Ép UUID đã sinh vào entity (tránh JPA tự sinh UUID khác)
        // Cách sạch nhất: dùng setId nếu entity có setter hoặc dùng @GeneratedValue tuỳ chỉnh
        // Ở đây persist bình thường rồi update payload sau save
        Equipment saved = equipmentRepository.save(equipment);

        // Cập nhật lại payload với UUID thật từ DB
        String finalPayload = qrBaseUrl + "?equipmentId=" + saved.getId();
        saved.setQrPayload(finalPayload);
        try {
            saved.setQrCode(generateQrBase64(finalPayload));
        } catch (Exception ignored) {}
        equipmentRepository.save(saved);

        return equipmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EquipmentResponse> getEquipmentByRoom(UUID roomId, UUID managerId, Pageable pageable) {
        return equipmentRepository.findAllByRoomId(roomId, pageable)
                .map(equipmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EquipmentResponse> getEquipmentByProperty(UUID propertyId, UUID managerId, Pageable pageable) {
        return equipmentRepository.findAllByPropertyId(propertyId, pageable)
                .map(equipmentMapper::toResponse);
    }

    @Override
    @Transactional
    public EquipmentResponse updateEquipment(UUID id, EquipmentRequest request, UUID managerId) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + id));

        equipmentMapper.updateEntityFromRequest(request, equipment);

        if (request.getRoomId() != null) {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với ID: " + request.getRoomId()));
            equipment.setRoom(room);
        }

        return equipmentMapper.toResponse(equipmentRepository.save(equipment));
    }

    @Override
    @Transactional
    public void deleteEquipment(UUID id, UUID managerId) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + id));
        equipmentRepository.delete(equipment);
    }

    @Override
    @Transactional(readOnly = true)
    public EquipmentResponse getEquipmentDetail(UUID id, UUID managerId) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + id));
        return equipmentMapper.toResponse(equipment);
    }

    // ─── Sinh QR Code ──────────────────────────────────────────────────────────
    private String generateQrBase64(String payload) throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 300, 300);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateMeterReadingRequest;
import com.sep490.slms2026.dto.response.MeterReadingResponse;
import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.MeterReadingRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.MeterReadingService;
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.util.UtilityTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MeterReadingServiceImpl implements MeterReadingService {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PropertyAccessService propertyAccessService;

    @Override
    @Transactional(readOnly = true)
    public MeterReadingResponse getLatestReading(Long propertyId, Long roomId, String type) {
        propertyAccessService.assertCanManageProperty(propertyId);
        UtilityType utilityType = UtilityTypeMapper.fromApi(type);
        loadProperty(propertyId);
        if (roomId != null) {
            loadRoom(propertyId, roomId);
        }

        Optional<MeterReading> latestReading = roomId == null
                ? meterReadingRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByRecordedAtDesc(
                        propertyId, utilityType)
                : meterReadingRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeOrderByRecordedAtDesc(
                        propertyId, roomId, utilityType);

        if (latestReading.isPresent()) {
            return toResponse(latestReading.get());
        }

        Optional<BigDecimal> fromInvoice = roomId == null
                ? utilityInvoiceRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByCreatedAtDesc(
                        propertyId, utilityType).map(i -> i.getNewReading())
                : utilityInvoiceRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeOrderByCreatedAtDesc(
                        propertyId, roomId, utilityType).map(i -> i.getNewReading());

        if (fromInvoice.isPresent()) {
            return MeterReadingResponse.builder()
                    .reading(fromInvoice.get())
                    .period("")
                    .recordedAt("")
                    .type(UtilityTypeMapper.toApi(utilityType))
                    .build();
        }

        BigDecimal initial = resolveInitialReading(propertyId, roomId, utilityType);
        return MeterReadingResponse.builder()
                .reading(initial)
                .period("")
                .recordedAt("")
                .type(UtilityTypeMapper.toApi(utilityType))
                .build();
    }

    @Override
    @Transactional
    public MeterReadingResponse recordReading(Long propertyId, Long roomId, CreateMeterReadingRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getType());
        Property property = loadProperty(propertyId);
        Room room = roomId == null ? null : loadRoom(propertyId, roomId);

        if (room != null && room.getStatus() == RoomStatus.DISABLED) {
            throw new BusinessException("Phòng đang ngưng khai thác — không ghi chỉ số");
        }

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        MeterReading saved = meterReadingRepository.save(MeterReading.builder()
                .property(property)
                .room(room)
                .utilityType(utilityType)
                .period(request.getPeriod())
                .reading(request.getReading())
                .imageUrl(request.getImageUrl())
                .recordedAt(LocalDateTime.now())
                .recordedBy(user.getId())
                .build());

        return toResponse(saved);
    }

    private BigDecimal resolveInitialReading(Long propertyId, Long roomId, UtilityType utilityType) {
        if (roomId != null) {
            return tenantContractRepository.findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                    .map(contract -> readingFromContract(contract, utilityType))
                    .orElse(BigDecimal.ZERO);
        }
        return tenantContractRepository.findByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE)
                .map(contract -> readingFromContract(contract, utilityType))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal readingFromContract(TenantContract contract, UtilityType utilityType) {
        BigDecimal reading = utilityType == UtilityType.ELECTRIC
                ? contract.getInitialElectricReading()
                : contract.getInitialWaterReading();
        return reading != null ? reading : BigDecimal.ZERO;
    }

    private Property loadProperty(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));
    }

    private Room loadRoom(Long propertyId, Long roomId) {
        return roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));
    }

    private MeterReadingResponse toResponse(MeterReading reading) {
        return MeterReadingResponse.builder()
                .reading(reading.getReading())
                .period(reading.getPeriod())
                .recordedAt(reading.getRecordedAt() != null ? reading.getRecordedAt().format(ISO_FORMAT) : "")
                .type(UtilityTypeMapper.toApi(reading.getUtilityType()))
                .imageUrl(reading.getImageUrl())
                .build();
    }
}

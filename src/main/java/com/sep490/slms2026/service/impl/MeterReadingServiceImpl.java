package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateMeterReadingRequest;
import com.sep490.slms2026.dto.response.MeterReadingResponse;
import com.sep490.slms2026.dto.response.PendingMeterReadingItem;
import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.MeterReadingRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UtilityBillRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.MeterReadingService;
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.util.ContractBillingCalendar;
import com.sep490.slms2026.util.UtilityTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    private final UtilityBillRepository utilityBillRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<PendingMeterReadingItem> listPending(String period) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean admin = user.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()));
        YearMonth month = ContractBillingCalendar.parsePeriod(period)
                .orElse(YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        String normalized = ContractBillingCalendar.normalizePeriod(month);

        List<UtilityBill> bills = utilityBillRepository.findPublishedByPeriodWithReadingDeadline(
                month.getMonthValue(), month.getYear(), UtilityBillStatus.PUBLISHED);

        List<PendingMeterReadingItem> items = new ArrayList<>();
        for (UtilityBill bill : bills) {
            Property property = bill.getProperty();
            if (property == null) {
                continue;
            }
            if (!admin && !user.getId().equals(property.getOperationManagerId())) {
                continue;
            }
            LocalDate readingDeadline = bill.getReadingDeadline();
            if (readingDeadline == null) {
                continue;
            }
            UtilityType type = bill.getType() != null ? bill.getType() : UtilityType.ELECTRIC;

            for (TenantContract contract : tenantContractRepository.findActiveWithTenantByPropertyId(property.getId())) {
                if (contract.getRoom() == null) {
                    continue;
                }
                if (contract.getStartDate() != null && contract.getStartDate().isAfter(readingDeadline)) {
                    continue;
                }
                Optional<MeterReading> reading = findReading(
                        property.getId(),
                        contract.getRoom().getId(),
                        type,
                        normalized);
                boolean hasReading = reading.isPresent();
                boolean hasPhoto = reading.filter(r -> r.getImageUrl() != null && !r.getImageUrl().isBlank()).isPresent();
                if (hasPhoto) {
                    continue;
                }
                int billingDay = ContractBillingCalendar.billingDayOfMonth(contract);
                items.add(PendingMeterReadingItem.builder()
                        .propertyId(property.getId())
                        .propertyName(property.getPropertyName())
                        .roomId(contract.getRoom().getId())
                        .roomNumber(contract.getRoom().getRoomNumber())
                        .contractId(contract.getId())
                        .utilityType(UtilityTypeMapper.toApi(type))
                        .period(normalized)
                        .billingDay(billingDay)
                        .meterDueDate(readingDeadline)
                        .hasReading(hasReading)
                        .hasPhoto(false)
                        .build());
            }
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPhoto(Long propertyId, Long roomId, UtilityType type, String period) {
        return findReading(propertyId, roomId, type, period)
                .filter(r -> r.getImageUrl() != null && !r.getImageUrl().isBlank())
                .isPresent();
    }

    private Optional<MeterReading> findReading(Long propertyId, Long roomId, UtilityType type, String period) {
        for (String candidate : periodAliases(period)) {
            Optional<MeterReading> found = roomId == null
                    ? meterReadingRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                            propertyId, type, candidate)
                    : meterReadingRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                            propertyId, roomId, type, candidate);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static List<String> periodAliases(String period) {
        List<String> aliases = new ArrayList<>();
        if (period != null && !period.isBlank()) {
            aliases.add(period.trim());
        }
        ContractBillingCalendar.parsePeriod(period).ifPresent(ym -> {
            String iso = ym.toString();
            String vn = String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
            if (!aliases.contains(iso)) {
                aliases.add(iso);
            }
            if (!aliases.contains(vn)) {
                aliases.add(vn);
            }
        });
        return aliases;
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

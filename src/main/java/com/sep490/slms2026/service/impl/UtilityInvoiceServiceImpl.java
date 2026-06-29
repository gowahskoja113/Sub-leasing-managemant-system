package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateUtilityInvoiceRequest;
import com.sep490.slms2026.dto.response.UtilityInvoiceHistoryResponse;
import com.sep490.slms2026.dto.response.UtilityInvoiceResponse;
import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UtilityInvoiceStatus;
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
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.service.UtilityInvoiceService;
import com.sep490.slms2026.util.UtilityTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UtilityInvoiceServiceImpl implements UtilityInvoiceService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PropertyAccessService propertyAccessService;

    @Override
    @Transactional
    public UtilityInvoiceResponse createRoomInvoice(Long propertyId, Long roomId, CreateUtilityInvoiceRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        Property property = loadProperty(propertyId);
        Room room = loadRoom(propertyId, roomId);
        validateRoomBillable(room);
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getType());
        validateInvoiceAmounts(request);

        TenantContract contract = tenantContractRepository
                .findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                .orElse(null);

        return createAndSend(property, room, contract, utilityType, request);
    }

    @Override
    @Transactional
    public UtilityInvoiceResponse createPropertyInvoice(Long propertyId, CreateUtilityInvoiceRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        Property property = loadProperty(propertyId);
        if (!Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException("API nguyên căn chỉ dùng cho nhà whole-house");
        }
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getType());
        validateInvoiceAmounts(request);

        TenantContract contract = tenantContractRepository
                .findByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE)
                .orElse(null);

        return createAndSend(property, null, contract, utilityType, request);
    }

    @Override
    @Transactional(readOnly = true)
    public UtilityInvoiceHistoryResponse listInvoices(Long propertyId, String period, String type) {
        propertyAccessService.assertCanManageProperty(propertyId);
        loadProperty(propertyId);

        UtilityType utilityType = type == null || type.isBlank() ? null : UtilityTypeMapper.fromApi(type);
        String periodFilter = period == null || period.isBlank() ? null : period.trim();

        List<UtilityInvoice> invoices = utilityInvoiceRepository.findByFilters(
                propertyId, periodFilter, utilityType);

        List<UtilityInvoiceResponse> items = invoices.stream().map(this::toResponse).toList();
        BigDecimal totalAmount = items.stream()
                .map(UtilityInvoiceResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Long> roomIds = new HashSet<>();
        for (UtilityInvoice invoice : invoices) {
            if (invoice.getRoom() != null) {
                roomIds.add(invoice.getRoom().getId());
            }
        }

        return UtilityInvoiceHistoryResponse.builder()
                .items(items)
                .totalCount(items.size())
                .totalAmount(totalAmount)
                .roomCount(roomIds.isEmpty() && !items.isEmpty() ? 1 : roomIds.size())
                .build();
    }

    private UtilityInvoiceResponse createAndSend(
            Property property,
            Room room,
            TenantContract contract,
            UtilityType utilityType,
            CreateUtilityInvoiceRequest request) {

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        UtilityInvoice invoice = utilityInvoiceRepository.save(UtilityInvoice.builder()
                .property(property)
                .room(room)
                .tenantContract(contract)
                .utilityType(utilityType)
                .billingPeriod(request.getBillingPeriod())
                .prevReading(request.getPrevReading())
                .newReading(request.getNewReading())
                .consumption(request.getConsumption())
                .unitPrice(request.getUnitPrice())
                .amount(request.getAmount())
                .meterImageUrl(request.getMeterImageUrl())
                .status(UtilityInvoiceStatus.SENT)
                .sentAt(now)
                .createdBy(user.getId())
                .createdAt(now)
                .build());

        meterReadingRepository.save(MeterReading.builder()
                .property(property)
                .room(room)
                .utilityType(utilityType)
                .period(request.getBillingPeriod())
                .reading(request.getNewReading())
                .imageUrl(request.getMeterImageUrl())
                .recordedAt(now)
                .recordedBy(user.getId())
                .build());

        UtilityInvoiceResponse response = toResponse(invoice);
        if (contract != null && contract.getTenant() != null && contract.getTenant().getUser() != null) {
            response.setTenantFullName(contract.getTenant().getUser().getFullName());
            response.setTenantPhone(contract.getTenant().getUser().getPhoneNumber());
        }
        return response;
    }

    private void validateRoomBillable(Room room) {
        if (room.getStatus() == RoomStatus.DISABLED) {
            throw new BusinessException("Phòng đang ngưng khai thác — không tạo hóa đơn");
        }
    }

    private void validateInvoiceAmounts(CreateUtilityInvoiceRequest request) {
        BigDecimal expectedConsumption = request.getNewReading().subtract(request.getPrevReading());
        if (expectedConsumption.compareTo(request.getConsumption()) != 0) {
            throw new BusinessException("Tiêu thụ không khớp (chỉ số mới − chỉ số cũ)");
        }
        BigDecimal expectedAmount = request.getConsumption()
                .multiply(request.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
        if (expectedAmount.compareTo(request.getAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException("Thành tiền không khớp (tiêu thụ × đơn giá)");
        }
        if (request.getNewReading().compareTo(request.getPrevReading()) < 0) {
            throw new BusinessException("Chỉ số mới phải lớn hơn hoặc bằng chỉ số cũ");
        }
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

    private UtilityInvoiceResponse toResponse(UtilityInvoice invoice) {
        UtilityInvoiceResponse.UtilityInvoiceResponseBuilder builder = UtilityInvoiceResponse.builder()
                .id(invoice.getId())
                .propertyId(invoice.getProperty().getId())
                .propertyName(invoice.getProperty().getPropertyName())
                .type(UtilityTypeMapper.toApi(invoice.getUtilityType()))
                .billingPeriod(invoice.getBillingPeriod())
                .prevReading(invoice.getPrevReading())
                .newReading(invoice.getNewReading())
                .consumption(invoice.getConsumption())
                .unitPrice(invoice.getUnitPrice())
                .amount(invoice.getAmount())
                .meterImageUrl(invoice.getMeterImageUrl())
                .status(invoice.getStatus())
                .sentAt(invoice.getSentAt())
                .createdAt(invoice.getCreatedAt());

        if (invoice.getRoom() != null) {
            builder.roomId(invoice.getRoom().getId())
                    .roomNumber(invoice.getRoom().getRoomNumber());
        }

        if (invoice.getTenantContract() != null && invoice.getTenantContract().getTenant() != null) {
            var tenantUser = invoice.getTenantContract().getTenant().getUser();
            if (tenantUser != null) {
                builder.tenantFullName(tenantUser.getFullName())
                        .tenantPhone(tenantUser.getPhoneNumber());
            }
        }

        return builder.build();
    }
}

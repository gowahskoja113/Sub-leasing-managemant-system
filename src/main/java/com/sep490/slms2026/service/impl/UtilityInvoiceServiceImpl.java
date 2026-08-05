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

import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.service.PushNotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class UtilityInvoiceServiceImpl implements UtilityInvoiceService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PropertyAccessService propertyAccessService;
    private final com.sep490.slms2026.service.TenantBillingService tenantBillingService;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    private final com.sep490.slms2026.repository.TenantInvoiceRepository tenantInvoiceRepository;

    @Override
    @Transactional
    public UtilityInvoiceResponse createRoomInvoice(Long propertyId, Long roomId, CreateUtilityInvoiceRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        Property property = loadProperty(propertyId);
        Room room = loadRoom(propertyId, roomId);
        validateRoomBillable(room);
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getType());
        validateBillingPeriodLock(propertyId, roomId, request.getBillingPeriod(), utilityType);
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
        validateBillingPeriodLock(propertyId, null, request.getBillingPeriod(), utilityType);
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

        if (contract != null) {
            tenantBillingService.createFromUtilityInvoice(invoice, contract);
            
            if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
                java.util.UUID tenantId = contract.getTenant().getUser().getId();
                String typeStr = utilityType == UtilityType.ELECTRIC ? "Điện" : "Nước";
                String title = "Hoá đơn " + typeStr + " mới";
                String content = String.format("Quản lý vừa chốt số và phát hành hoá đơn %s kỳ %s. Số tiền: %,dđ.",
                        typeStr, request.getBillingPeriod(), request.getAmount().longValue());
                
                Notification notification = Notification.builder()
                        .userId(tenantId)
                        .title(title)
                        .content(content)
                        .type("UTILITY_INVOICE_CREATED")
                        .screen("InvoiceList")
                        .paramsJson("{\"invoiceId\": " + invoice.getId() + "}")
                        .read(false)
                        .build();
                notificationRepository.save(notification);

                String pushToken = contract.getTenant().getUser().getPushToken();
                if (pushToken != null && !pushToken.isBlank()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("screen", "InvoiceList");
                    pushNotificationService.sendPushNotification(pushToken, title, content, data);
                }
            }
        }

        UtilityInvoiceResponse response = toResponse(invoice);
        if (contract != null && contract.getTenant() != null && contract.getTenant().getUser() != null) {
            response.setTenantFullName(contract.getTenant().getUser().getFullName());
            response.setTenantPhone(contract.getTenant().getUser().getPhoneNumber());
        }
        return response;
    }

    private void validateBillingPeriodLock(Long propertyId, Long roomId, String billingPeriod, UtilityType utilityType) {
        if (java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).getDayOfMonth() > 10) {
            throw new BusinessException("409: UTILITY_WINDOW_CLOSED - Đã qua ngày 10, không thể tạo mới hoá đơn điện/nước cho kỳ này.");
        }

        List<UtilityInvoice> utilities = utilityInvoiceRepository.findByFilters(propertyId, billingPeriod, utilityType);
        if (roomId != null) {
            utilities = utilities.stream().filter(u -> u.getRoom() != null && u.getRoom().getId().equals(roomId)).toList();
        } else {
            utilities = utilities.stream().filter(u -> u.getRoom() == null).toList();
        }

        if (!utilities.isEmpty()) {
            boolean allPaid = true;
            for (UtilityInvoice ui : utilities) {
                var ti = tenantInvoiceRepository.findByUtilityInvoiceId(ui.getId());
                if (ti.isPresent()) {
                    if (ti.get().getStatus() != com.sep490.slms2026.enums.TenantInvoiceStatus.PAID && 
                        ti.get().getStatus() != com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED) {
                        allPaid = false;
                        break;
                    }
                } else {
                    allPaid = false;
                    break;
                }
            }
            if (allPaid) {
                throw new BusinessException("409: PERIOD_ALREADY_SETTLED - Kỳ cước này đã được tất toán, không thể tạo thêm hoá đơn.");
            }
        }
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

package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateUtilityBillRequest;
import com.sep490.slms2026.dto.request.CreateUtilityBillRequest;
import com.sep490.slms2026.dto.response.UtilityBillResponse;
import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.UtilityBillRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.UtilityBillService;
import com.sep490.slms2026.service.UserPushTokenService;
import com.sep490.slms2026.util.UtilityTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityBillServiceImpl implements UtilityBillService {

    /** Đủ chữ số để consumption × unitPrice khớp totalAmount, không làm tròn về VND nguyên. */
    static final int UNIT_PRICE_SCALE = 8;

    private final UtilityBillRepository utilityBillRepository;
    private final PropertyRepository propertyRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserPushTokenService userPushTokenService;



    @Override
    @Transactional
    public UtilityBillResponse createUtilityBill(CreateUtilityBillRequest request) {
        return createPublishedBill(
                request.getPropertyId(),
                UtilityTypeMapper.fromApi(request.getType()),
                request.getBillingPeriod(),
                request.getMonth(),
                request.getYear(),
                request.getTotalQuantity(),
                request.getTotalAmount(),
                request.getImageUrl(),
                false);
    }

    private UtilityBillResponse createPublishedBill(
            Long propertyId,
            UtilityType type,
            String billingPeriod,
            Integer month,
            Integer year,
            Integer totalQuantity,
            BigDecimal totalAmount,
            String imageUrl,
            boolean evnLegacyCodes) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy toà nhà ID: " + propertyId));

        Optional<UtilityBill> existing = utilityBillRepository.findByPropertyIdAndMonthAndYearAndTypeAndStatus(
                propertyId, month, year, type, UtilityBillStatus.PUBLISHED);
        if (existing.isPresent()) {
            if (evnLegacyCodes) {
                throw new BusinessException("EVN_BILL_ALREADY_EXISTS",
                        "Đã tồn tại hoá đơn EVN cho kỳ này.");
            }
            String label = type == UtilityType.WATER ? "nước" : "điện";
            throw new BusinessException("UTILITY_BILL_ALREADY_EXISTS",
                    "Đã tồn tại hoá đơn " + label + " cho kỳ này.");
        }

        BigDecimal unitPrice = totalAmount.divide(
                new BigDecimal(totalQuantity), UNIT_PRICE_SCALE, RoundingMode.HALF_UP);

        UtilityBill bill = UtilityBill.builder()
                .property(property)
                .type(type)
                .billingPeriod(billingPeriod)
                .month(month)
                .year(year)
                .totalQuantity(totalQuantity)
                .totalAmount(totalAmount)
                .unitPrice(unitPrice)
                .imageUrl(imageUrl)
                .status(UtilityBillStatus.PUBLISHED)
                .createdBy(user.getId())
                .createdAt(LocalDateTime.now())
                .build();

        utilityBillRepository.save(bill);
        notifyManagerBillPublished(property, bill);

        return toResponse(bill, user.getUsername());
    }



    @Override
    @Transactional
    public void revokeUtilityBill(Long id) {
        revoke(id, false);
    }

    private void revoke(Long id, boolean evnLegacyCodes) {
        UtilityBill bill = utilityBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        evnLegacyCodes ? "Không tìm thấy hoá đơn EVN." : "Không tìm thấy hoá đơn tiện ích."));

        if (bill.getStatus() == UtilityBillStatus.REVOKED) {
            return;
        }

        UtilityType invoiceType = bill.getType() != null ? bill.getType() : UtilityType.ELECTRIC;
        List<UtilityInvoice> utilityInvoices = utilityInvoiceRepository.findByFilters(
                bill.getProperty().getId(), bill.getBillingPeriod(), invoiceType);

        if (!utilityInvoices.isEmpty()) {
            if (evnLegacyCodes) {
                throw new BusinessException("EVN_BILL_IN_USE",
                        "Không thể thu hồi vì đã có hoá đơn điện được gửi cho khách trong kỳ này.");
            }
            String label = invoiceType == UtilityType.WATER ? "nước" : "điện";
            throw new BusinessException("UTILITY_BILL_IN_USE",
                    "Không thể thu hồi vì đã có hoá đơn " + label + " được gửi cho khách trong kỳ này.");
        }

        bill.setStatus(UtilityBillStatus.REVOKED);
        utilityBillRepository.save(bill);
    }



    @Override
    @Transactional(readOnly = true)
    public List<UtilityBillResponse> getUtilityBills(
            Long propertyId, Integer month, Integer year, UtilityType type, boolean isManager) {
        UtilityBillStatus statusFilter = isManager ? UtilityBillStatus.PUBLISHED : null;
        List<UtilityBill> bills;

        if (isManager) {
            CustomUserDetails user = SecurityUtils.requireCurrentUser();
            if (propertyId != null) {
                if (!propertyRepository.findIdsByOperationManagerId(user.getId()).contains(propertyId)) {
                    throw new AccessDeniedException("Bạn không có quyền quản lý nhà này");
                }
                bills = utilityBillRepository.findByFilters(propertyId, month, year, type, statusFilter);
            } else {
                List<Long> managerPropIds = propertyRepository.findIdsByOperationManagerId(user.getId());
                if (managerPropIds.isEmpty()) {
                    return List.of();
                }
                bills = managerPropIds.stream()
                        .flatMap(pid -> utilityBillRepository.findByFilters(pid, month, year, type, statusFilter).stream())
                        .collect(Collectors.toList());
            }
        } else {
            bills = utilityBillRepository.findByFilters(propertyId, month, year, type, statusFilter);
        }

        return bills.stream().map(bill -> {
            String username = null;
            if (bill.getCreatedBy() != null) {
                username = userRepository.findById(bill.getCreatedBy())
                    .map(u -> u.getUsername()).orElse("unknown");
            }
            return toResponse(bill, username);
        }).collect(Collectors.toList());
    }

    private void notifyManagerBillPublished(Property property, UtilityBill bill) {
        UUID managerId = property.getOperationManagerId();
        if (managerId == null) {
            managerId = property.getManagedBy();
        }
        if (managerId == null) {
            log.warn("Utility bill {} published but property {} has no operation manager — skip notify",
                    bill.getId(), property.getId());
            return;
        }

        boolean water = bill.getType() == UtilityType.WATER;
        String rentalType = Boolean.TRUE.equals(property.getWholeHouse()) ? "NGUYEN_CAN" : "THEO_PHONG";
        String unit = water ? "m³" : "kWh";
        String kind = water ? "nước" : "điện";
        String notifType = water ? "WATER_BILL_PUBLISHED" : "EVN_BILL_PUBLISHED";
        String title = "Đã có hoá đơn " + kind + " kỳ " + bill.getMonth() + "/" + bill.getYear();
        String content = (property.getPropertyName() != null ? property.getPropertyName() : "Nhà")
                + " · " + rentalType
                + " · " + bill.getTotalQuantity() + " " + unit
                + " · đơn giá " + formatVnPrice(bill.getUnitPrice()) + "đ/" + unit;
        String paramsJson = "{\"propertyId\":" + property.getId() + "}";

        notificationRepository.save(Notification.builder()
                .userId(managerId)
                .title(title)
                .content(content)
                .type(notifType)
                .screen("UtilityBilling")
                .paramsJson(paramsJson)
                .read(false)
                .build());

        Map<String, Object> data = new HashMap<>();
        data.put("type", notifType);
        data.put("screen", "UtilityBilling");
        data.put("propertyId", property.getId());
        data.put("params", Map.of("propertyId", property.getId()));
        userPushTokenService.sendToUser(managerId, title, content, data);
    }

    private static String formatVnPrice(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        if (rounded.stripTrailingZeros().scale() <= 0) {
            return String.format("%,d", rounded.longValue()).replace(',', '.');
        }
        return String.format("%,.2f", rounded.doubleValue()).replace(',', '@').replace('.', ',').replace('@', '.');
    }

    private UtilityBillResponse toResponse(UtilityBill bill, String username) {
        UtilityType type = bill.getType() != null ? bill.getType() : UtilityType.ELECTRIC;
        Integer qty = bill.getTotalQuantity();
        return UtilityBillResponse.builder()
                .id(bill.getId())
                .propertyId(bill.getProperty().getId())
                .propertyName(bill.getProperty().getPropertyName())
                .type(UtilityTypeMapper.toApi(type))
                .billingPeriod(bill.getBillingPeriod())
                .month(bill.getMonth())
                .year(bill.getYear())
                .totalQuantity(qty)
                .totalAmount(bill.getTotalAmount())
                .unitPrice(bill.getUnitPrice())
                .unitPriceExact(bill.getUnitPrice())
                .imageUrl(bill.getImageUrl())
                .status(bill.getStatus().name())
                .createdBy(username)
                .createdAt(bill.getCreatedAt())
                .build();
    }
}


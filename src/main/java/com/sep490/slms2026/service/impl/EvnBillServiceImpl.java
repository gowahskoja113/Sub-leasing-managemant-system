package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateEvnBillRequest;
import com.sep490.slms2026.dto.response.EvnBillResponse;
import com.sep490.slms2026.entity.EvnBill;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.EvnBillStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.EvnBillRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.EvnBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvnBillServiceImpl implements EvnBillService {

    private final EvnBillRepository evnBillRepository;
    private final PropertyRepository propertyRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public EvnBillResponse createEvnBill(CreateEvnBillRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();

        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy toà nhà ID: " + request.getPropertyId()));

        Optional<EvnBill> existing = evnBillRepository.findByPropertyIdAndMonthAndYearAndStatus(
                request.getPropertyId(), request.getMonth(), request.getYear(), EvnBillStatus.PUBLISHED);
        if (existing.isPresent()) {
            throw new BusinessException("409: Đã tồn tại hoá đơn EVN cho kỳ này.");
        }

        BigDecimal unitPrice = request.getTotalAmount().divide(new BigDecimal(request.getTotalKwh()), 0, RoundingMode.HALF_UP);

        EvnBill bill = EvnBill.builder()
                .property(property)
                .billingPeriod(request.getBillingPeriod())
                .month(request.getMonth())
                .year(request.getYear())
                .totalKwh(request.getTotalKwh())
                .totalAmount(request.getTotalAmount())
                .unitPrice(unitPrice)
                .imageUrl(request.getImageUrl())
                .status(EvnBillStatus.PUBLISHED)
                .createdBy(user.getId())
                .createdAt(LocalDateTime.now())
                .build();

        evnBillRepository.save(bill);

        return toResponse(bill, user.getUsername());
    }

    @Override
    @Transactional
    public void revokeEvnBill(Long id) {
        EvnBill bill = evnBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoá đơn EVN."));

        if (bill.getStatus() == EvnBillStatus.REVOKED) {
            return;
        }

        List<UtilityInvoice> utilityInvoices = utilityInvoiceRepository.findByFilters(
                bill.getProperty().getId(), bill.getBillingPeriod(), UtilityType.ELECTRIC);
        
        // Cần chặn thu hồi khi kỳ đó đã có hoá đơn điện được gửi
        // Trong hệ thống, nếu tồn tại utility invoice thì là đã gửi (vì UtilityInvoice đại diện cho việc chốt/gửi)
        if (!utilityInvoices.isEmpty()) {
            throw new BusinessException("409: Không thể thu hồi vì đã có hoá đơn điện được gửi cho khách trong kỳ này.");
        }

        bill.setStatus(EvnBillStatus.REVOKED);
        evnBillRepository.save(bill);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvnBillResponse> getEvnBills(Long propertyId, Integer month, Integer year, boolean isManager) {
        EvnBillStatus statusFilter = isManager ? EvnBillStatus.PUBLISHED : null;
        List<EvnBill> bills;

        if (isManager) {
            CustomUserDetails user = SecurityUtils.requireCurrentUser();
            if (propertyId != null) {
                // Kiểm tra quyền
                if (!propertyRepository.findIdsByOperationManagerId(user.getId()).contains(propertyId)) {
                    throw new BusinessException("403: Bạn không có quyền quản lý nhà này");
                }
                bills = evnBillRepository.findByFilters(propertyId, month, year, statusFilter);
            } else {
                List<Long> managerPropIds = propertyRepository.findIdsByOperationManagerId(user.getId());
                if (managerPropIds.isEmpty()) {
                    return List.of();
                }
                // Thay vì query từng nhà, có thể query DB nhưng EvnBillRepository.findByFilters chỉ nhận 1 propertyId
                // Vì là list nên ta cứ fetch cho tất cả các nhà quản lý (nếu ko quá nhiều)
                bills = managerPropIds.stream()
                        .flatMap(pid -> evnBillRepository.findByFilters(pid, month, year, statusFilter).stream())
                        .collect(Collectors.toList());
            }
        } else {
            bills = evnBillRepository.findByFilters(propertyId, month, year, statusFilter);
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

    private EvnBillResponse toResponse(EvnBill bill, String username) {
        return EvnBillResponse.builder()
                .id(bill.getId())
                .propertyId(bill.getProperty().getId())
                .propertyName(bill.getProperty().getPropertyName())
                .billingPeriod(bill.getBillingPeriod())
                .month(bill.getMonth())
                .year(bill.getYear())
                .totalKwh(bill.getTotalKwh())
                .totalAmount(bill.getTotalAmount())
                .unitPrice(bill.getUnitPrice())
                .imageUrl(bill.getImageUrl())
                .status(bill.getStatus().name())
                .createdBy(username)
                .createdAt(bill.getCreatedAt())
                .build();
    }
}

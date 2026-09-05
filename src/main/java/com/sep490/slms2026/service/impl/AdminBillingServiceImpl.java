package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.AdminHostDto;
import com.sep490.slms2026.dto.response.AdminInvoiceDto;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PaymentStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.AdminBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sep490.slms2026.dto.response.AdminDepositDto;

@Service
@RequiredArgsConstructor
public class AdminBillingServiceImpl implements AdminBillingService {

    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminHostDto> getAdminHosts() {
        return userRepository.findByRole(Role.ROLE_OWNER).stream()
                .map(u -> new AdminHostDto(u.getId(), u.getFullName()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminDepositDto> getAdminDeposits(String status, Pageable pageable) {
        PaymentStatus paymentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
            } catch (Exception e) {}
        }

        return tenantContractRepository.findAdminDeposits(paymentStatus, false, pageable)
                .map(contract -> {
                    String tenantPhone = null;
                    if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
                        tenantPhone = contract.getTenant().getUser().getPhoneNumber();
                    }
                    return AdminDepositDto.builder()
                            .contractId(contract.getId())
                            .contractCode(contract.getContractCode())
                            .propertyName(contract.getProperty() != null ? contract.getProperty().getPropertyName() : null)
                            .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                            .tenantName(contract.getTenant() != null && contract.getTenant().getUser() != null ? contract.getTenant().getUser().getFullName() : null)
                            .tenantPhone(tenantPhone)
                            .deposit(contract.getDeposit())
                            .depositMonths(contract.getDepositMonths())
                            .rentAmount(contract.getRentAmount())
                            .paymentStatus(contract.getPaymentStatus() != null ? contract.getPaymentStatus().name() : null)
                            .depositMethod(contract.getPayosOrderCode() != null ? "PAYOS" : (contract.getDepositCashManagerConfirmedAt() != null || contract.getDepositCashTenantConfirmedAt() != null ? "CASH" : null))
                            .depositPaidAt(contract.getPaidAt() != null ? contract.getPaidAt() : contract.getDepositCashManagerConfirmedAt())
                            .contractStatus(contract.getStatus() != null ? contract.getStatus().name() : null)
                            .moveInDate(contract.getMoveInDate())
                            .build();
                });
    }

    private YearMonth parseMonth(String month, YearMonth defaultMonth) {
        try {
            return YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (Exception e) {
            return defaultMonth;
        }
    }

    private <T> Page<T> slicePage(List<T> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        if (start > list.size()) {
            return new PageImpl<>(new ArrayList<>(), pageable, list.size());
        }
        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }
}

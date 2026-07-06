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

@Service
@RequiredArgsConstructor
public class AdminBillingServiceImpl implements AdminBillingService {

    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminInvoiceDto> getAdminInvoices(String month, String hostId, String status, String keyword, Pageable pageable) {
        YearMonth ym = month != null && !month.isBlank() ? parseMonth(month, YearMonth.now()) : YearMonth.now();
        List<AdminInvoiceDto> allInvoices = buildInvoices(ym, hostId, status, keyword);
        
        // Sort by due date descending
        allInvoices.sort((a, b) -> b.getDueDate().compareTo(a.getDueDate()));

        return slicePage(allInvoices, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminHostDto> getAdminHosts() {
        return userRepository.findByRole(Role.ROLE_OWNER).stream()
                .map(u -> new AdminHostDto(u.getId(), u.getFullName()))
                .collect(Collectors.toList());
    }

    private List<AdminInvoiceDto> buildInvoices(YearMonth ym, String hostIdFilter, String statusFilter, String keyword) {
        LocalDate dueDate = ym.atEndOfMonth().plusDays(5);
        LocalDate today = LocalDate.now();
        List<AdminInvoiceDto> invoices = new ArrayList<>();

        for (TenantContract contract : tenantContractRepository.findByStatus(ContractStatus.ACTIVE)) {
            if (!isContractActiveInMonth(contract, ym)) {
                continue;
            }

            // Filter by hostId
            if (hostIdFilter != null && !hostIdFilter.isBlank()) {
                UUID contractHostId = contract.getProperty().getManagedBy();
                if (contractHostId == null || !contractHostId.toString().equals(hostIdFilter)) {
                    continue;
                }
            }

            String currentStatus;
            if (contract.getPaymentStatus() == PaymentStatus.PAID
                    && contract.getPaidAt() != null
                    && ym.equals(YearMonth.from(contract.getPaidAt()))) {
                currentStatus = "PAID";
            } else if (dueDate.isBefore(today)) {
                currentStatus = "OVERDUE";
            } else {
                currentStatus = "UNPAID";
            }

            // Filter by status
            if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase(currentStatus)) {
                continue;
            }

            String invoiceId = contract.getId() + "-" + ym;
            String tenantName = contract.getTenant().getUser().getFullName();
            String buildingName = contract.getProperty().getPropertyName();
            String roomCode = contract.getRoom() != null ? contract.getRoom().getRoomNumber() : "NGUYEN_CAN";
            
            // Filter by keyword (invoiceId, tenantName, buildingName)
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                boolean matches = invoiceId.toLowerCase().contains(kw)
                        || (tenantName != null && tenantName.toLowerCase().contains(kw))
                        || (buildingName != null && buildingName.toLowerCase().contains(kw));
                if (!matches) {
                    continue;
                }
            }

            UUID hostUuid = contract.getProperty().getManagedBy();
            String hostName = "Unknown";
            if (hostUuid != null) {
                var hostUser = userRepository.findById(hostUuid).orElse(null);
                if (hostUser != null) {
                    hostName = hostUser.getFullName();
                }
            }

            invoices.add(AdminInvoiceDto.builder()
                    .id(invoiceId)
                    .hostId(hostUuid != null ? hostUuid.toString() : null)
                    .hostName(hostName)
                    .buildingName(buildingName)
                    .tenantName(tenantName)
                    .roomCode(roomCode)
                    .amount(contract.getRentAmount())
                    .dueDate(dueDate)
                    .status(currentStatus)
                    .build());
        }
        return invoices;
    }

    private boolean isContractActiveInMonth(TenantContract contract, YearMonth ym) {
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        if (contract.getStartDate().isAfter(monthEnd)) {
            return false;
        }
        return contract.getEndDate() == null || !contract.getEndDate().isBefore(monthStart);
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

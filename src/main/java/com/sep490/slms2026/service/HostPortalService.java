package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.host.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface HostPortalService {

    Page<HostNotificationDto> listNotifications(UUID userId, Boolean unreadOnly, Pageable pageable);

    void markNotificationRead(UUID userId, Long notificationId);

    void markAllNotificationsRead(UUID userId);

    HostDashboardSummaryResponse getDashboardSummary(String month);

    HostCashflowResponse getCashflow(String from, String to);

    HostExpenseBreakdownResponse getExpenseBreakdown(String month);

    HostPropertyPnlResponse getPropertyPnl(String month);

    HostReceivablesAgingResponse getReceivablesAging();

    HostDepositsResponse getDeposits(String status);

    Page<HostInvoiceDto> listInvoices(String month, String status, Pageable pageable);

    Page<HostExpenseDto> listExpenses(Long propertyId, String category, String month, Pageable pageable);

    HostExpenseDto createExpense(HostExpenseUpsertRequest request);

    HostExpenseDto updateExpense(Long id, Map<String, Object> patch);

    void deleteExpense(Long id);

    List<HostFinancialSummaryRow> getFinancialSummary(String from, String to);

    List<HostManagerPerformanceRow> getManagerPerformance(String month);

    List<HostPropertyPerformanceRow> getPropertyPerformance(String month);

    Page<HostContractDto> listContracts(Long propertyId, String status, Pageable pageable);

    HostContractDto approveContract(Long contractId);

    HostContractDto rejectContract(Long contractId, String reason);

    List<MasterLeaseDto> listMasterLeases(String status, Long propertyId);

    MasterLeaseDto getMasterLease(Long id);

    MasterLeaseDto createMasterLease(MasterLeaseUpsertRequest request);

    MasterLeaseDto updateMasterLease(Long id, Map<String, Object> patch);

    MasterLeaseDto terminateMasterLease(Long id);
}

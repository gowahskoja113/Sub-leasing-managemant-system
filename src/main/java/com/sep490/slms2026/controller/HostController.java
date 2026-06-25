package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.host.*;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.HostPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/host")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class HostController {

    private final HostPortalService hostPortalService;

    @GetMapping("/notifications")
    public ResponseEntity<Page<HostNotificationDto>> listNotifications(
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = currentUserId();
        return ResponseEntity.ok(hostPortalService.listNotifications(
                userId, unreadOnly, PageRequest.of(page, size)));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable Long id) {
        hostPortalService.markNotificationRead(currentUserId(), id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllNotificationsRead() {
        hostPortalService.markAllNotificationsRead(currentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<HostDashboardSummaryResponse> dashboardSummary(@RequestParam String month) {
        return ResponseEntity.ok(hostPortalService.getDashboardSummary(month));
    }

    @GetMapping("/finance/cashflow")
    public ResponseEntity<HostCashflowResponse> cashflow(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(hostPortalService.getCashflow(from, to));
    }

    @GetMapping("/finance/expense-breakdown")
    public ResponseEntity<HostExpenseBreakdownResponse> expenseBreakdown(@RequestParam String month) {
        return ResponseEntity.ok(hostPortalService.getExpenseBreakdown(month));
    }

    @GetMapping("/finance/property-pnl")
    public ResponseEntity<HostPropertyPnlResponse> propertyPnl(@RequestParam String month) {
        return ResponseEntity.ok(hostPortalService.getPropertyPnl(month));
    }

    @GetMapping("/finance/receivables-aging")
    public ResponseEntity<HostReceivablesAgingResponse> receivablesAging() {
        return ResponseEntity.ok(hostPortalService.getReceivablesAging());
    }

    @GetMapping("/finance/deposits")
    public ResponseEntity<HostDepositsResponse> deposits(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(hostPortalService.getDeposits(status));
    }

    @GetMapping("/invoices")
    public ResponseEntity<Page<HostInvoiceDto>> invoices(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(hostPortalService.listInvoices(month, status, PageRequest.of(page, size)));
    }

    @GetMapping("/expenses")
    public ResponseEntity<Page<HostExpenseDto>> listExpenses(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(hostPortalService.listExpenses(
                propertyId, category, month, PageRequest.of(page, size)));
    }

    @PostMapping("/expenses")
    public ResponseEntity<HostExpenseDto> createExpense(@Valid @RequestBody HostExpenseUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hostPortalService.createExpense(request));
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<HostExpenseDto> updateExpense(
            @PathVariable Long id,
            @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(hostPortalService.updateExpense(id, patch));
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        hostPortalService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports/financial-summary")
    public ResponseEntity<List<HostFinancialSummaryRow>> financialSummary(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(hostPortalService.getFinancialSummary(from, to));
    }

    @GetMapping("/reports/manager-performance")
    public ResponseEntity<List<HostManagerPerformanceRow>> managerPerformance(@RequestParam String month) {
        return ResponseEntity.ok(hostPortalService.getManagerPerformance(month));
    }

    @GetMapping("/reports/property-performance")
    public ResponseEntity<List<HostPropertyPerformanceRow>> propertyPerformance(@RequestParam String month) {
        return ResponseEntity.ok(hostPortalService.getPropertyPerformance(month));
    }

    @GetMapping("/contracts")
    public ResponseEntity<Page<HostContractDto>> listContracts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(hostPortalService.listContracts(status, PageRequest.of(page, size)));
    }

    @PutMapping("/contracts/{id}/approve")
    public ResponseEntity<HostContractDto> approveContract(@PathVariable Long id) {
        return ResponseEntity.ok(hostPortalService.approveContract(id));
    }

    @PutMapping("/contracts/{id}/reject")
    public ResponseEntity<HostContractDto> rejectContract(
            @PathVariable Long id,
            @Valid @RequestBody HostRejectContractRequest request) {
        return ResponseEntity.ok(hostPortalService.rejectContract(id, request.getReason()));
    }

    @GetMapping("/master-leases")
    public ResponseEntity<List<MasterLeaseDto>> listMasterLeases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long propertyId) {
        return ResponseEntity.ok(hostPortalService.listMasterLeases(status, propertyId));
    }

    @GetMapping("/master-leases/{id}")
    public ResponseEntity<MasterLeaseDto> getMasterLease(@PathVariable Long id) {
        return ResponseEntity.ok(hostPortalService.getMasterLease(id));
    }

    @PostMapping("/master-leases")
    public ResponseEntity<MasterLeaseDto> createMasterLease(@Valid @RequestBody MasterLeaseUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hostPortalService.createMasterLease(request));
    }

    @PutMapping("/master-leases/{id}")
    public ResponseEntity<MasterLeaseDto> updateMasterLease(
            @PathVariable Long id,
            @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(hostPortalService.updateMasterLease(id, patch));
    }

    @PostMapping("/master-leases/{id}/terminate")
    public ResponseEntity<MasterLeaseDto> terminateMasterLease(@PathVariable Long id) {
        return ResponseEntity.ok(hostPortalService.terminateMasterLease(id));
    }

    private static UUID currentUserId() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return user.getId();
    }
}

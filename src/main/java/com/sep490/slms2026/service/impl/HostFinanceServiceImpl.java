package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.*;
import com.sep490.slms2026.entity.Expense;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.MasterLease;
import com.sep490.slms2026.enums.DepositStatus;
import com.sep490.slms2026.enums.ExpenseCategory;
import com.sep490.slms2026.enums.InvoiceStatus;
import com.sep490.slms2026.enums.MasterLeaseStatus;
import com.sep490.slms2026.repository.ExpenseRepository;
import com.sep490.slms2026.repository.InvoiceRepository;
import com.sep490.slms2026.repository.MasterLeaseRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.service.HostFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HostFinanceServiceImpl implements HostFinanceService {

    private final ExpenseRepository expenseRepository;
    private final PropertyRepository propertyRepository;
    private final MasterLeaseRepository masterLeaseRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    public Object getCashflow(String from, String to) {
        // Implementation for Cashflow
        return new HashMap<>(); // Placeholder for FE to hook into
    }

    @Override
    public Object getExpenseBreakdown(String month) {
        List<Expense> expenses = expenseRepository.findByMonthAndDeletedFalse(month);
        Map<ExpenseCategory, BigDecimal> breakdown = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
                        
        List<Map<String, Object>> result = new ArrayList<>();
        breakdown.forEach((cat, amt) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("category", cat.name());
            map.put("amount", amt);
            result.add(map);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("month", month);
        response.put("breakdown", result);
        return response;
    }

    @Override
    public PropertyPnlResponse getPropertyPnl(String month) {
        List<Property> properties = propertyRepository.findAll();
        List<PropertyPnlResponse.PropertyPnlRow> rows = new ArrayList<>();
        
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalLeaseCost = BigDecimal.ZERO;
        BigDecimal totalOtherExpense = BigDecimal.ZERO;

        for (Property prop : properties) {
            // Simplified revenue calculation for demo, ideally joins with Room rent prices
            BigDecimal revenue = BigDecimal.ZERO; 
            
            // Lease Cost
            BigDecimal leaseCost = BigDecimal.ZERO;
            List<MasterLease> leases = masterLeaseRepository.findByStatusAndDeletedFalse(MasterLeaseStatus.ACTIVE);
            for (MasterLease lease : leases) {
                if (lease.getProperty().getId().equals(prop.getId())) {
                    leaseCost = leaseCost.add(lease.getMonthlyRent());
                }
            }

            // Other Expenses
            List<Expense> expenses = expenseRepository.findByPropertyIdAndMonthAndCategoryNotAndDeletedFalse(
                    prop.getId(), month, ExpenseCategory.LEASE);
            BigDecimal otherExpense = expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalPropExpense = leaseCost.add(otherExpense);
            BigDecimal net = revenue.subtract(totalPropExpense);
            Double marginPct = revenue.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : net.doubleValue() / revenue.doubleValue() * 100;

            PropertyPnlResponse.PropertyPnlRow row = new PropertyPnlResponse.PropertyPnlRow();
            row.setPropertyId(prop.getId());
            row.setPropertyName(prop.getPropertyName());
            row.setRevenue(revenue);
            row.setLeaseCost(leaseCost);
            row.setOtherExpense(otherExpense);
            row.setTotalExpense(totalPropExpense);
            row.setNet(net);
            row.setMarginPct(marginPct);
            
            rows.add(row);
            
            totalRevenue = totalRevenue.add(revenue);
            totalLeaseCost = totalLeaseCost.add(leaseCost);
            totalOtherExpense = totalOtherExpense.add(otherExpense);
        }

        BigDecimal sumExpense = totalLeaseCost.add(totalOtherExpense);
        BigDecimal sumNet = totalRevenue.subtract(sumExpense);
        Double totalMargin = totalRevenue.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : sumNet.doubleValue() / totalRevenue.doubleValue() * 100;

        PropertyPnlResponse.PnlTotals totals = new PropertyPnlResponse.PnlTotals();
        totals.setRevenue(totalRevenue);
        totals.setLeaseCost(totalLeaseCost);
        totals.setOtherExpense(totalOtherExpense);
        totals.setTotalExpense(sumExpense);
        totals.setNet(sumNet);
        totals.setMarginPct(totalMargin);

        PropertyPnlResponse response = new PropertyPnlResponse();
        response.setMonth(month);
        response.setRows(rows);
        response.setTotals(totals);

        return response;
    }

    @Override
    public Page<InvoiceResponse> getInvoices(String month, InvoiceStatus status, Pageable pageable) {
        return invoiceRepository.findByMonthAndStatusAndDeletedFalse(month, status, pageable)
            .map(inv -> {
                InvoiceResponse res = new InvoiceResponse();
                res.setId(inv.getId().toString());
                if (inv.getTenant() != null && inv.getTenant().getUser() != null) res.setTenantName(inv.getTenant().getUser().getFullName());
                if (inv.getRoom() != null) res.setRoomCode(inv.getRoom().getRoomNumber());
                if (inv.getProperty() != null) res.setPropertyName(inv.getProperty().getPropertyName());
                res.setAmount(inv.getAmount());
                res.setDueDate(inv.getDueDate());
                res.setStatus(inv.getStatus());
                return res;
            });
    }

    @Override
    public ReceivablesAgingResponse getReceivablesAging() {
        return new ReceivablesAgingResponse(); // Placeholder
    }

    @Override
    public DepositLedgerResponse getDepositLedger(DepositStatus status) {
        return new DepositLedgerResponse(); // Placeholder
    }
}

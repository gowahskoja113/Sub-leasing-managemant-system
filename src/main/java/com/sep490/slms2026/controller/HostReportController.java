package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.FinancialSummaryDTO;
import com.sep490.slms2026.dto.response.ManagerPerformanceDTO;
import com.sep490.slms2026.dto.response.PropertyPerformanceDTO;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/host/reports")
public class HostReportController {

    @GetMapping("/financial-summary")
    public List<FinancialSummaryDTO> getFinancialSummary(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        // TODO: Implement actual aggregation query
        List<FinancialSummaryDTO> list = new ArrayList<>();
        list.add(FinancialSummaryDTO.builder()
                .month("2026-06")
                .revenue(new java.math.BigDecimal("50000000"))
                .expense(new java.math.BigDecimal("10000000"))
                .netProfit(new java.math.BigDecimal("40000000"))
                .marginPct(80.0)
                .occupancyRate(90.5)
                .build());
        return list;
    }

    @GetMapping("/manager-performance")
    public List<ManagerPerformanceDTO> getManagerPerformance(@RequestParam(required = false) String month) {
        // TODO: Implement actual aggregation query
        List<ManagerPerformanceDTO> list = new ArrayList<>();
        list.add(ManagerPerformanceDTO.builder()
                .managerId(1L)
                .managerName("Nguyen Van A")
                .phone("0901234567")
                .propertyCount(3)
                .activeTenants(20)
                .occupancyRate(85.0)
                .resolvedMaintenance(5)
                .openMaintenance(1)
                .build());
        return list;
    }

    @GetMapping("/property-performance")
    public List<PropertyPerformanceDTO> getPropertyPerformance(@RequestParam(required = false) String month) {
        // TODO: Implement actual aggregation query
        List<PropertyPerformanceDTO> list = new ArrayList<>();
        list.add(PropertyPerformanceDTO.builder()
                .propertyId(1L)
                .propertyName("Tòa nhà Cầu Giấy")
                .address("123 Cầu Giấy")
                .occupancyRate(95.0)
                .occupiedRooms(19)
                .totalRooms(20)
                .monthlyRevenue(new java.math.BigDecimal("80000000"))
                .openMaintenance(2)
                .build());
        return list;
    }
}

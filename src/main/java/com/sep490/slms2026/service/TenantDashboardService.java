package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.TenantDashboardResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.util.TenantActiveContractResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantDashboardService {

    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;

    public TenantDashboardResponse getDashboard() {
        return getDashboard(null);
    }

    public TenantDashboardResponse getDashboard(Long contractId) {
        CustomUserDetails userDetails = SecurityUtils.requireCurrentUser();

        List<TenantContract> all = tenantContractRepository.findByTenantId(userDetails.getId());
        List<TenantContract> activeList = TenantActiveContractResolver.listActive(all);

        if (activeList.isEmpty()) {
            return TenantDashboardResponse.builder()
                    .contracts(List.of())
                    .summary(TenantDashboardResponse.ActivitySummary.builder()
                            .overdueInvoiceCount(0)
                            .overdueTotal(BigDecimal.ZERO)
                            .maintenancePending(0)
                            .maintenanceInProgress(0)
                            .unreadNotifications(0)
                            .build())
                    .build();
        }

        List<TenantDashboardResponse.ContractSummary> contractSummaries = activeList.stream()
                .map(this::toContractSummary)
                .toList();

        TenantContract activeContract;
        try {
            // Dashboard: không bắt buộc contractId khi nhiều HĐ — pick latest cho primary (FE cũ)
            activeContract = TenantActiveContractResolver.resolve(all, contractId, true);
        } catch (BusinessException e) {
            throw e;
        }

        Room room = activeContract.getRoom();
        Property property = activeContract.getProperty();

        TenantDashboardResponse.RoomSummary roomSummary;
        if (room != null) {
            roomSummary = TenantDashboardResponse.RoomSummary.builder()
                    .id(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .floor(room.getFloor())
                    .area(room.getArea())
                    .depositAmount(activeContract.getDeposit())
                    .build();
        } else {
            roomSummary = TenantDashboardResponse.RoomSummary.builder()
                    .id(null)
                    .roomNumber(property.getPropertyName())
                    .floor(null)
                    .area(property.getAreaSize())
                    .depositAmount(activeContract.getDeposit())
                    .build();
        }

        TenantDashboardResponse.ContractSummary contractSummary = toContractSummary(activeContract);

        String managerName = null;
        String managerPhone = null;
        java.util.UUID managerId = property.getOperationManagerId() != null 
                ? property.getOperationManagerId() 
                : property.getManagedBy();
        if (managerId != null) {
            User manager = userRepository.findById(managerId).orElse(null);
            if (manager != null) {
                managerName = manager.getFullName();
                managerPhone = manager.getPhoneNumber();
            }
        }

        TenantDashboardResponse.BuildingSummary buildingSummary = TenantDashboardResponse.BuildingSummary.builder()
                .propertyId(property.getId())
                .name(property.getPropertyName())
                .address(property.getAddress())
                .totalFloors(property.getTotalFloor())
                .electricityRate(property.getElectricityUnitPrice())
                .waterRate(property.getWaterUnitPrice())
                .serviceCharge(property.getServiceFee())
                .hostName(null)
                .hostPhone(null)
                .managerName(managerName)
                .managerPhone(managerPhone)
                .build();

        TenantDashboardResponse.ActivitySummary activitySummary = TenantDashboardResponse.ActivitySummary.builder()
                .overdueInvoiceCount(0)
                .overdueTotal(BigDecimal.ZERO)
                .maintenancePending(0)
                .maintenanceInProgress(0)
                .unreadNotifications(0)
                .build();

        return TenantDashboardResponse.builder()
                .room(roomSummary)
                .contract(contractSummary)
                .building(buildingSummary)
                .summary(activitySummary)
                .contracts(contractSummaries)
                .build();
    }

    private TenantDashboardResponse.ContractSummary toContractSummary(TenantContract c) {
        Long daysLeft = null;
        if (c.getEndDate() != null) {
            daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
        }
        Property property = c.getProperty();
        Room room = c.getRoom();
        return TenantDashboardResponse.ContractSummary.builder()
                .id(c.getId())
                .code(c.getContractCode())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .daysLeft(daysLeft)
                .status(c.getStatus().name())
                .propertyId(property != null ? property.getId() : null)
                .propertyName(property != null ? property.getPropertyName() : null)
                .roomId(room != null ? room.getId() : null)
                .roomNumber(room != null ? room.getRoomNumber()
                        : (property != null ? property.getPropertyName() : null))
                .build();
    }
}

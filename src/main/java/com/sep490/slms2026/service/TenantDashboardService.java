package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.TenantDashboardResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
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
        CustomUserDetails userDetails = SecurityUtils.requireCurrentUser();

        List<TenantContract> contracts = tenantContractRepository.findByTenantId(userDetails.getId());
        
        TenantContract activeContract = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (activeContract == null) {
            // Return 200 with null values per requirement
            return TenantDashboardResponse.builder()
                    .summary(TenantDashboardResponse.ActivitySummary.builder()
                            .overdueInvoiceCount(0)
                            .overdueTotal(BigDecimal.ZERO)
                            .maintenancePending(0)
                            .maintenanceInProgress(0)
                            .unreadNotifications(0)
                            .build())
                    .build();
        }

        Room room = activeContract.getRoom();
        Property property = activeContract.getProperty();
        User host = null; // We can get host from property.getManagedBy() or operationManager, but let's just get property createdBy or managedBy.
        // Wait, property.getCreatedBy() or getManagedBy(). For now we can use dummy or fetch host if needed. 
        // According to current schema, we don't have direct host info on Property unless we query userRepository.
        // For now, leaving host info as placeholder or null if we don't fetch it here.

        TenantDashboardResponse.RoomSummary roomSummary = null;
        if (room != null) {
            roomSummary = TenantDashboardResponse.RoomSummary.builder()
                    .id(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .floor(room.getFloor())
                    .area(room.getArea())
                    .depositAmount(activeContract.getDeposit())
                    .build();
        } else {
            // Whole house
            roomSummary = TenantDashboardResponse.RoomSummary.builder()
                    .id(null)
                    .roomNumber(property.getPropertyName()) // use property name for roomNumber as specified
                    .floor(null)
                    .area(property.getAreaSize())
                    .depositAmount(activeContract.getDeposit())
                    .build();
        }

        Long daysLeft = null;
        if (activeContract.getEndDate() != null) {
            daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), activeContract.getEndDate());
        }

        TenantDashboardResponse.ContractSummary contractSummary = TenantDashboardResponse.ContractSummary.builder()
                .id(activeContract.getId())
                .code(activeContract.getContractCode())
                .startDate(activeContract.getStartDate())
                .endDate(activeContract.getEndDate())
                .daysLeft(daysLeft)
                .status(activeContract.getStatus().name())
                .build();

        String managerName = null;
        String managerPhone = null;
        if (property.getManagedBy() != null) {
            User manager = userRepository.findById(property.getManagedBy()).orElse(null);
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
                .hostName(null) // Mocked for now, update if userRepository is injected
                .hostPhone(null)
                .managerName(managerName)
                .managerPhone(managerPhone)
                .build();

        // Summary is mocked because invoice/maintenance/notification are out of scope for this document
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
                .build();
    }
}

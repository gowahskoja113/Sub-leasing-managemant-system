package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRequestRepository repository;
    private final com.sep490.slms2026.service.PropertyImageStorage imageStorage;
    private final com.sep490.slms2026.repository.RoomRepository roomRepository;
    private final com.sep490.slms2026.repository.EquipmentRepository equipmentRepository;
    private final com.sep490.slms2026.repository.ExpenseRepository expenseRepository;

    @Override
    public MaintenanceRequestResponse uploadPhotos(Long id, java.util.List<org.springframework.web.multipart.MultipartFile> files, String type) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        java.util.List<String> newUrls = new java.util.ArrayList<>();
        String prefix = "MAINT-" + id;
        
        for (org.springframework.web.multipart.MultipartFile file : files) {
            try {
                String url = imageStorage.store(prefix, file.getOriginalFilename(), file.getBytes());
                newUrls.add(url);
            } catch (Exception e) {
                throw new RuntimeException("Failed to upload photo", e);
            }
        }
        
        if (!newUrls.isEmpty()) {
            if ("BEFORE".equalsIgnoreCase(type)) {
                String existing = req.getBeforeImageUrls();
                req.setBeforeImageUrls(existing == null || existing.isEmpty() ? String.join(",", newUrls) : existing + "," + String.join(",", newUrls));
            } else if ("AFTER".equalsIgnoreCase(type)) {
                String existing = req.getAfterImageUrls();
                req.setAfterImageUrls(existing == null || existing.isEmpty() ? String.join(",", newUrls) : existing + "," + String.join(",", newUrls));
            } else {
                throw new IllegalArgumentException("Type must be BEFORE or AFTER");
            }
            repository.save(req);
        }
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse acknowledge(Long id, MaintenanceAcknowledgeRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setStatus(MaintenanceStatus.ACKNOWLEDGED);
        req.setAcknowledgedAt(LocalDateTime.now());
        req.setTechnicianId(request.getTechnicianId());
        // set note
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse schedule(Long id, MaintenanceScheduleRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setStatus(MaintenanceStatus.SCHEDULED);
        req.setScheduledSlots(String.join(",", request.getScheduledSlots()));
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse confirmSchedule(Long id, MaintenanceConfirmScheduleRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setConfirmedSlot(request.getSlot());
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse updateStatus(Long id, MaintenanceStatusRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setStatus(request.getStatus());
        req.setOnHoldReason(request.getOnHoldReason());
        
        if (request.getStatus() == MaintenanceStatus.IN_PROGRESS) {
            if (req.getRoom() != null) {
                req.getRoom().setStatus(com.sep490.slms2026.enums.RoomStatus.MAINTENANCE);
                roomRepository.save(req.getRoom());
            }
        } else if (request.getStatus() == MaintenanceStatus.CANCELLED) {
            com.sep490.slms2026.security.CustomUserDetails user = com.sep490.slms2026.security.SecurityUtils.requireCurrentUser();
            String roleName = user.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_TENANT".equals(roleName)) {
                throw new org.springframework.security.access.AccessDeniedException("Tenant không có quyền hủy yêu cầu");
            }
            if ("ROLE_MANAGER".equals(roleName)) {
                if (req.getProperty() == null || 
                    (!user.getId().equals(req.getProperty().getOperationManagerId()) && 
                     !user.getId().equals(req.getProperty().getManagedBy()))) {
                    throw new org.springframework.security.access.AccessDeniedException("Manager không quản lý property này");
                }
            }
            if (req.getRoom() != null) {
                req.getRoom().setStatus(req.getTenant() != null ? com.sep490.slms2026.enums.RoomStatus.RENTED : com.sep490.slms2026.enums.RoomStatus.AVAILABLE);
                roomRepository.save(req.getRoom());
            }
        }
        
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse resolve(Long id, MaintenanceResolveRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setRepairCost(request.getRepairCost());
        req.setCostPaidBy(request.getCostPaidBy());
        req.setCause(request.getCause());
        req.setResolutionNote(request.getResolutionNote());
        req.setEquipmentId(request.getEquipmentId());
        
        // thresholds
        if (request.getRepairCost() != null && request.getRepairCost().doubleValue() > 2000000) {
            req.setStatus(MaintenanceStatus.PENDING_APPROVAL);
        } else {
            req.setStatus(MaintenanceStatus.DONE);
            req.setDoneAt(LocalDateTime.now());
        }
        
        if (request.getEquipmentId() != null) {
            equipmentRepository.findById(request.getEquipmentId()).ifPresent(eq -> {
                // If repair cost > 1 million or cause is severe, recommend replacement
                if (request.getRepairCost() != null && request.getRepairCost().doubleValue() > 1000000) {
                    eq.setRecommendReplacement(true);
                    equipmentRepository.save(eq);
                }
            });
        }
        
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse approve(Long id, MaintenanceApproveRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        if (request.isApprove()) {
            req.setStatus(MaintenanceStatus.DONE);
            req.setDoneAt(LocalDateTime.now());
        } else {
            req.setStatus(MaintenanceStatus.IN_PROGRESS);
        }
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse confirm(Long id, MaintenanceConfirmRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        if (request.isAccept()) {
            req.setStatus(MaintenanceStatus.CONFIRMED);
            req.setTenantConfirmedAt(LocalDateTime.now());
            
            // Auto Expense creation for Host
            if (req.getCostPaidBy() == com.sep490.slms2026.enums.CostPaidBy.HOST && req.getRepairCost() != null) {
                com.sep490.slms2026.entity.Expense expense = new com.sep490.slms2026.entity.Expense();
                expense.setProperty(req.getProperty());
                expense.setCategory(com.sep490.slms2026.enums.ExpenseCategory.MAINTENANCE);
                expense.setAmount(req.getRepairCost());
                expense.setMonth(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
                expense.setNote("Chi phí bảo trì ticket #" + req.getId() + ": " + req.getTitle());
                expense.setCreatedBy("SYSTEM");
                expenseRepository.save(expense);
            }
            
            // Revert room status
            if (req.getRoom() != null) {
                req.getRoom().setStatus(req.getTenant() != null ? com.sep490.slms2026.enums.RoomStatus.RENTED : com.sep490.slms2026.enums.RoomStatus.AVAILABLE);
                roomRepository.save(req.getRoom());
            }
        } else {
            req.setStatus(MaintenanceStatus.REOPENED);
            req.setReopenCount(req.getReopenCount() + 1);
        }
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public Page<MaintenanceRequestResponse> getRequests(Pageable pageable) {
        return repository.findByDeletedFalse(pageable).map(this::convertToResponse);
    }

    private MaintenanceRequestResponse convertToResponse(MaintenanceRequest req) {
        MaintenanceRequestResponse res = new MaintenanceRequestResponse();
        res.setId(req.getId());
        res.setTitle(req.getTitle());
        res.setDescription(req.getDescription());
        res.setStatus(req.getStatus());
        res.setCostPaidBy(req.getCostPaidBy());
        res.setCause(req.getCause());
        res.setRepairCost(req.getRepairCost());
        return res;
    }
}

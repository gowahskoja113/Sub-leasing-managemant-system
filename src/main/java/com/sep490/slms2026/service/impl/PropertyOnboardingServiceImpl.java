package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.*;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.EquipmentImportAction;
import com.sep490.slms2026.enums.EquipmentOperationalStatus;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RenovationSessionStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ConflictException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.DepreciationService;
import com.sep490.slms2026.service.PropertyDeletionService;
import com.sep490.slms2026.service.PropertyOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropertyOnboardingServiceImpl implements PropertyOnboardingService {

    private final PropertyRepository propertyRepository;
    private final ZoneRepository zoneRepository;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final EquipmentManifestRepository equipmentManifestRepository;
    private final EquipmentRepository equipmentRepository;
    private final RenovationCategoryRepository renovationCategoryRepository;
    private final RenovationLineRepository renovationLineRepository;
    private final RenovationSessionRepository renovationSessionRepository;
    private final RoomRepository roomRepository;
    private final InboundContractRepository inboundContractRepository;
    private final DepreciationResultRepository depreciationResultRepository;
    private final DepreciationService depreciationService;
    private final UserRepository userRepository;
    private final PropertyDeletionService propertyDeletionService;
    private final HandoverEquipmentRepository handoverEquipmentRepository;
    private final RenovationSessionViewMapper renovationSessionViewMapper;

    @Override
    @Transactional
    public PropertyResponse createDraft(PropertyDraftRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khu vực (Zone)"));

        String shortAddress = request.getAddress().trim();
        String fullAddress = buildFullAddress(shortAddress, zone);
        assertAddressAvailable(fullAddress, null);

        Property property = Property.builder()
                .propertyName(request.getPropertyName())
                .address(fullAddress)
                .zone(zone)
                .descriptions(request.getDescriptions())
                .areaSize(request.getAreaSize())
                .length(request.getLength())
                .width(request.getWidth())
                .totalFloor(request.getTotalFloor())
                .totalRooms(request.getTotalRooms())
                .imageUrls(request.getImageUrls())
                .status(PropertyStatus.DRAFT)
                .build();

        return mapPropertyResponse(propertyRepository.save(property), shortAddress);
    }

    @Override
    @Transactional
    public PropertyResponse setOnboardingOptions(Long propertyId, OnboardingOptionsRequest request) {
        Property property = findOnboardingProperty(propertyId);
        if (property.getStatus() != PropertyStatus.PENDING) {
            throw new BusinessException(
                    "Phải hoàn thành quy trình 1 (ký hợp đồng) trước khi chọn loại hình thuê/cải tạo");
        }

        property.setWholeHouse(request.getWholeHouse());
        property.setHasRenovation(request.getHasRenovation());

        if (!request.getHasRenovation()) {
            property.setRenovationCompleted(true);
            property.setRenovationStartDate(null);
            property.setRenovationEndDate(null);
        } else {
            property.setRenovationCompleted(false);
        }

        property.setStatus(PropertyStatus.UNDER_RENOVATION);
        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public PropertyResponse updateStructure(Long propertyId, UpdatePropertyStructureRequest request) {
        Property property = findOnboardingProperty(propertyId);
        property.setTotalFloor(request.getTotalFloor());
        property.setTotalRooms(request.getTotalRooms());

        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public List<EquipmentManifestResponse> saveEquipmentManifest(Long propertyId,
                                                                 SaveEquipmentManifestRequest request) {
        Property property = findOnboardingProperty(propertyId);
        if (property.getStatus() != PropertyStatus.UNDER_RENOVATION
                && property.getStatus() != PropertyStatus.PENDING_EQUIPMENT_INSTALLATION) {
            throw new BusinessException(
                    "Phải hoàn thành quy trình 2 trước khi khai báo thiết bị");
        }
        equipmentManifestRepository.deleteByPropertyId(propertyId);

        List<EquipmentManifest> saved = new ArrayList<>();
        for (EquipmentManifestItemRequest item : request.getItems()) {
            validateEquipmentStatus(item.getStatus());
            EquipmentCatalog catalog = equipmentCatalogRepository.findById(item.getCatalogId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy danh mục thiết bị ID=" + item.getCatalogId()));

            java.math.BigDecimal itemPrice = item.getPrice() != null ? item.getPrice() : java.math.BigDecimal.ZERO;
            if (item.getSource() == com.sep490.slms2026.enums.EquipmentSource.INITIAL_HANDOVER) {
                itemPrice = java.math.BigDecimal.ZERO;
            }

            saved.add(equipmentManifestRepository.save(EquipmentManifest.builder()
                    .property(property)
                    .catalog(catalog)
                    .quantity(item.getQuantity())
                    .status(item.getStatus())
                    .source(item.getSource())
                    .price(itemPrice)
                    .build()));
        }

        if (property.getStatus() == PropertyStatus.UNDER_RENOVATION) {
            property.setStatus(PropertyStatus.PENDING_EQUIPMENT_INSTALLATION);
            propertyRepository.save(property);
        }

        return saved.stream().map(this::toManifestResponse).toList();
    }

    @Override
    @Transactional
    public List<EquipmentManifestResponse> appendPurchasedEquipmentManifest(Long propertyId,
                                                                            SaveEquipmentManifestRequest request) {
        Property property = findOnboardingProperty(propertyId);
        if (property.getStatus() != PropertyStatus.UNDER_RENOVATION
                && property.getStatus() != PropertyStatus.PENDING_EQUIPMENT_INSTALLATION) {
            throw new BusinessException(
                    "Chỉ bổ sung thiết bị khi nhà đang cải tạo (UNDER_RENOVATION hoặc PENDING_EQUIPMENT_INSTALLATION)");
        }

        List<EquipmentManifest> saved = new ArrayList<>();
        for (EquipmentManifestItemRequest item : request.getItems()) {
            validateEquipmentStatus(item.getStatus());
            if (item.getSource() != EquipmentSource.PURCHASED) {
                throw new BusinessException("appendPurchasedEquipmentManifest chỉ hỗ trợ nguồn PURCHASED");
            }
            EquipmentCatalog catalog = equipmentCatalogRepository.findById(item.getCatalogId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy danh mục thiết bị ID=" + item.getCatalogId()));

            java.math.BigDecimal itemPrice = item.getPrice() != null ? item.getPrice() : java.math.BigDecimal.ZERO;
            EquipmentManifest manifest = findPurchasedManifestByPrice(
                    propertyId, catalog.getId(), item.getStatus(), itemPrice);

            if (manifest != null) {
                manifest.setQuantity(manifest.getQuantity() + item.getQuantity());
                saved.add(equipmentManifestRepository.save(manifest));
            } else {
                saved.add(equipmentManifestRepository.save(EquipmentManifest.builder()
                        .property(property)
                        .catalog(catalog)
                        .quantity(item.getQuantity())
                        .status(item.getStatus())
                        .source(EquipmentSource.PURCHASED)
                        .price(itemPrice)
                        .build()));
            }
        }

        if (property.getStatus() == PropertyStatus.UNDER_RENOVATION) {
            property.setStatus(PropertyStatus.PENDING_EQUIPMENT_INSTALLATION);
            propertyRepository.save(property);
        }

        return saved.stream().map(this::toManifestResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentManifestResponse> getEquipmentManifest(Long propertyId) {
        ensurePropertyExists(propertyId);
        return equipmentManifestRepository.findByPropertyId(propertyId).stream()
                .map(this::toManifestResponse)
                .toList();
    }

    @Override
    @Transactional
    public EquipmentResponse assignEquipment(Long propertyId, AssignEquipmentRequest request) {
        Property property = findOnboardingProperty(propertyId);
        if (property.getStatus() != PropertyStatus.PENDING_EQUIPMENT_INSTALLATION) {
            throw new BusinessException(
                    "Chỉ gán thiết bị khi nhà đang ở quy trình 3 (PENDING_EQUIPMENT_INSTALLATION)");
        }
        validateEquipmentStatus(request.getStatus());
        validateEquipmentPlacement(request.getRoomId(), request.getHouseArea());

        EquipmentImportAction importAction = request.getImportAction() != null
                ? request.getImportAction()
                : EquipmentImportAction.THEM_MOI;

        EquipmentCatalog catalog = equipmentCatalogRepository.findById(request.getCatalogId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy danh mục thiết bị ID=" + request.getCatalogId()));

        EquipmentManifest manifest = null;
        BigDecimal unitPrice;

        if (request.getSource() == EquipmentSource.INITIAL_HANDOVER) {
            manifest = equipmentManifestRepository
                    .findByPropertyIdAndCatalogIdAndStatusAndSource(
                            propertyId, request.getCatalogId(), request.getStatus(), request.getSource())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(String.format(
                            "Thiết bị catalog ID=%d trạng thái %s nguồn %s chưa có trong manifest bàn giao",
                            request.getCatalogId(), request.getStatus(), request.getSource())));

            long alreadyAssigned = equipmentRepository.countActiveByManifestId(manifest.getId());
            if (alreadyAssigned + request.getQuantity() > manifest.getQuantity()) {
                throw new BusinessException(String.format(
                        "Catalog ID=%d (%s): đã gán %d/%d — không thể gán thêm %d",
                        request.getCatalogId(), request.getStatus(),
                        alreadyAssigned, manifest.getQuantity(), request.getQuantity()));
            }
            unitPrice = BigDecimal.ZERO;
        } else {
            unitPrice = resolvePurchasedUnitPrice(request);
            manifest = findPurchasedManifestByPrice(
                    propertyId, request.getCatalogId(), request.getStatus(), unitPrice);

            if (manifest != null) {
                long alreadyAssigned = equipmentRepository.countActiveByManifestId(manifest.getId());
                if (alreadyAssigned + request.getQuantity() > manifest.getQuantity()) {
                    throw new BusinessException(String.format(
                            "Catalog ID=%d (%s): đã gán %d/%d — không thể gán thêm %d",
                            request.getCatalogId(), request.getStatus(),
                            alreadyAssigned, manifest.getQuantity(), request.getQuantity()));
                }
            }
        }

        Room room = resolveRoomForAssignment(property, propertyId, request.getRoomId(), request.getHouseArea());

        if (request.getSource() == EquipmentSource.PURCHASED
                && importAction == EquipmentImportAction.THAY_THE) {
            disableReplacedPurchasedEquipment(propertyId, request.getCatalogId(),
                    request.getRoomId(), request.getHouseArea(), request.getQuantity());
        }

        RenovationSession renovationSession = renovationSessionRepository
                .findTopByPropertyIdAndEndDateIsNullOrderBySessionNumberDesc(propertyId)
                .orElse(null);

        Equipment lastSaved = null;
        for (int i = 0; i < request.getQuantity(); i++) {
            lastSaved = equipmentRepository.save(Equipment.builder()
                    .property(property)
                    .room(room)
                    .catalog(catalog)
                    .manifest(manifest)
                    .renovationSession(renovationSession)
                    .operationalStatus(EquipmentOperationalStatus.ACTIVE)
                    .houseArea(request.getHouseArea())
                    .source(request.getSource())
                    .status(request.getStatus())
                    .price(unitPrice)
                    .note(request.getNote())
                    .warrantyMonths(request.getWarrantyMonths())
                    .warrantyStartDate(request.getWarrantyStartDate())
                    .warrantyEndDate(request.getWarrantyEndDate())
                    .build());
        }
        return toEquipmentResponse(lastSaved);
    }

    private void disableReplacedPurchasedEquipment(Long propertyId,
                                                   Long catalogId,
                                                   Long roomId,
                                                   HouseArea houseArea,
                                                   int quantity) {
        List<Equipment> activeAtPlacement = equipmentRepository.findActivePurchasedAtPlacement(
                propertyId, catalogId, roomId, houseArea);
        if (activeAtPlacement.size() < quantity) {
            throw new BusinessException(
                    "THAY_THE: không đủ thiết bị đang ACTIVE tại vị trí này (cần thay "
                            + quantity + ", hiện có " + activeAtPlacement.size() + ")");
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < quantity; i++) {
            Equipment existing = activeAtPlacement.get(i);
            existing.setOperationalStatus(EquipmentOperationalStatus.DISABLED);
            existing.setDisabledAt(now);
            equipmentRepository.save(existing);
        }
    }

    private BigDecimal resolvePurchasedUnitPrice(AssignEquipmentRequest request) {
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Thiết bị mua mới (PURCHASED) phải có price > 0");
        }
        return request.getPrice();
    }

    private EquipmentManifest findPurchasedManifestByPrice(Long propertyId, Long catalogId,
                                                           EquipmentStatus status, BigDecimal price) {
        BigDecimal normalizedPrice = price != null ? price : BigDecimal.ZERO;
        return equipmentManifestRepository
                .findByPropertyIdAndCatalogIdAndStatusAndSource(
                        propertyId, catalogId, status, EquipmentSource.PURCHASED)
                .stream()
                .filter(existing -> {
                    BigDecimal existingPrice = existing.getPrice() != null
                            ? existing.getPrice() : BigDecimal.ZERO;
                    return existingPrice.compareTo(normalizedPrice) == 0;
                })
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public RenovationLineResponse addRenovationLine(Long propertyId, AddRenovationLineRequest request) {
        Property property = findOnboardingProperty(propertyId);
        if (property.getStatus() != PropertyStatus.UNDER_RENOVATION
                && property.getStatus() != PropertyStatus.PENDING_EQUIPMENT_INSTALLATION) {
            throw new BusinessException("Chỉ thêm hạng mục cải tạo khi nhà đang ở quy trình 2");
        }
        if (!Boolean.TRUE.equals(property.getHasRenovation())) {
            throw new BusinessException("Tòa nhà không chọn cải tạo — không thể thêm hạng mục");
        }

        RenovationCategory category = renovationCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy danh mục cải tạo ID=" + request.getCategoryId()));

        RenovationSession session = findOrCreateCurrentSession(property);

        RenovationLine saved = renovationLineRepository.save(RenovationLine.builder()
                .property(property)
                .category(category)
                .session(session)
                .cost(request.getCost())
                .note(request.getNote())
                .build());
        return toRenovationLineResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RenovationLineResponse> getRenovationLines(Long propertyId) {
        ensurePropertyExists(propertyId);
        return renovationLineRepository.findByPropertyId(propertyId).stream()
                .map(this::toRenovationLineResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RenovationSessionResponse> getRenovationSessions(Long propertyId) {
        ensurePropertyExists(propertyId);
        return renovationSessionViewMapper.listHistoryNewestFirst(propertyId);
    }

    @Override
    @Transactional
    public PropertyResponse setRenovationSchedule(Long propertyId, RenovationScheduleRequest request) {
        Property property = findOnboardingProperty(propertyId);
        if (property.getStatus() != PropertyStatus.UNDER_RENOVATION
                && property.getStatus() != PropertyStatus.PENDING_EQUIPMENT_INSTALLATION) {
            throw new BusinessException("Chỉ nhập lịch cải tạo khi nhà đang ở quy trình 2");
        }
        if (!Boolean.TRUE.equals(property.getHasRenovation())) {
            throw new BusinessException("Tòa nhà không chọn cải tạo");
        }
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BusinessException("Ngày kết thúc cải tạo phải sau ngày bắt đầu");
        }
        property.setRenovationStartDate(request.getStartDate());
        property.setRenovationEndDate(request.getEndDate());
        property.setRenovationCompleted(false);

        RenovationSession session = findOrCreateCurrentSession(property);
        session.setStartDate(request.getStartDate());
        session.setEndDate(request.getEndDate());
        renovationSessionRepository.save(session);

        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public PropertyResponse startRenovation(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() != PropertyStatus.ACTIVE) {
            throw new BusinessException("Chỉ có thể cải tạo lại khi tòa nhà đang ACTIVE");
        }

        if (Boolean.FALSE.equals(property.getWholeHouse())) {
            long rentedCount = roomRepository.countByPropertyIdAndStatus(propertyId, RoomStatus.RENTED);
            if (rentedCount > 0) {
                throw new BusinessException(
                        "Còn " + rentedCount + " phòng đang có khách thuê — không thể cải tạo");
            }
        }

        property.setStatus(PropertyStatus.UNDER_RENOVATION);
        property.setRenovationCompleted(false);

        int nextSessionNumber = renovationSessionRepository.findMaxSessionNumberByPropertyId(propertyId) + 1;
        renovationSessionRepository.save(RenovationSession.builder()
                .property(property)
                .sessionNumber(nextSessionNumber)
                .startDate(LocalDate.now())
                .status(RenovationSessionStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build());

        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public PropertyResponse completeRenovation(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() == PropertyStatus.PENDING_EQUIPMENT_INSTALLATION) {
            if (Boolean.TRUE.equals(property.getHasRenovation())) {
                LocalDate endDate = property.getRenovationEndDate() != null
                        ? property.getRenovationEndDate()
                        : LocalDate.now();
                finalizeCurrentRenovationSession(property, endDate);
            }
            property.setRenovationCompleted(true);
            property.setStatus(PropertyStatus.RENOVATION_COMPLETED);
            return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
        }

        if (!Boolean.TRUE.equals(property.getHasRenovation())) {
            throw new BusinessException("Tòa nhà không có cải tạo");
        }

        if (property.getStatus() == PropertyStatus.UNDER_RENOVATION) {
            boolean isSupplementRenovation = renovationSessionRepository
                    .findTopByPropertyIdAndEndDateIsNullOrderBySessionNumberDesc(propertyId)
                    .map(session -> session.getSessionNumber() >= 2)
                    .orElse(false);
            finalizeCurrentRenovationSession(property, LocalDate.now());
            property.setRenovationCompleted(true);
            if (isSupplementRenovation) {
                property.setStatus(PropertyStatus.RENOVATION_COMPLETED);
            } else if (property.getOperationManagerId() != null) {
                property.setStatus(PropertyStatus.ACTIVE);
            } else {
                property.setStatus(PropertyStatus.PENDING_HOST_REVIEW);
            }
        } else {
            throw new BusinessException(
                    "Chỉ có thể xác nhận hoàn thành khi nhà đang PENDING_EQUIPMENT_INSTALLATION hoặc UNDER_RENOVATION (cải tạo lại)");
        }

        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public OnboardingSummaryResponse submitToHost(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() != PropertyStatus.RENOVATION_COMPLETED) {
            throw new BusinessException(
                    "Phải hoàn thành 3 quy trình onboarding (trạng thái RENOVATION_COMPLETED) trước khi gửi host");
        }
        validateReadyForSubmit(property);

        depreciationService.calculate(propertyId, CalculateDepreciationRequest.builder().build());

        property.setSubmittedToHostAt(LocalDateTime.now());
        property.setStatus(PropertyStatus.PENDING_HOST_REVIEW);
        propertyRepository.save(property);

        return getOnboardingSummary(propertyId);
    }

    @Override
    @Transactional
    public PropertyActivationResponse hostConfirm(Long propertyId, HostConfirmRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() != PropertyStatus.PENDING_HOST_REVIEW) {
            throw new BusinessException(
                    "Chỉ có thể xác nhận khi nhà ở trạng thái PENDING_HOST_REVIEW");
        }
        if (Boolean.TRUE.equals(property.getHasRenovation()) && !property.isRenovationCompleted()) {
            throw new BusinessException("Phải hoàn tất cải tạo trước khi host xác nhận giá");
        }
        if (!depreciationResultRepository.existsByPropertyId(propertyId)) {
            throw new BusinessException("Chưa có kết quả tính giá — admin phải gửi host trước");
        }

        property.setHostContingencyPercent(request.getContingencyPercent());
        property.setStatus(PropertyStatus.PENDING_OPERATION_MANAGER);

        PropertyActivationResponse response;
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            response = confirmWholeHouse(property, propertyId, request);
        } else {
            response = confirmPerRoom(property, propertyId, request);
        }

        return response;
    }

    @Override
    @Transactional
    public PropertyActivationResponse assignOperationManager(Long propertyId,
                                                             AssignOperationManagerRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() != PropertyStatus.PENDING_OPERATION_MANAGER
                && property.getStatus() != PropertyStatus.ACTIVE
                && property.getStatus() != PropertyStatus.RENTED) {
            throw new BusinessException(
                    "Chỉ có thể gán/đổi Operation Manager khi nhà đang PENDING_OPERATION_MANAGER, ACTIVE hoặc RENTED");
        }

        UUID managerId = request.getOperationManagerId();
        validateOperationManager(managerId);

        if (property.getStatus() == PropertyStatus.ACTIVE || property.getStatus() == PropertyStatus.RENTED) {
            if (!managerId.equals(property.getOperationManagerId())) {
                property.setOperationManagerId(managerId);
                property.setManagedBy(managerId);
                propertyRepository.save(property);
            }
            if (Boolean.TRUE.equals(property.getWholeHouse())) {
                return buildWholeHouseActivationResponse(property, propertyId);
            }
            return buildActivePerRoomActivationResponse(property, propertyId);
        }

        property.setOperationManagerId(managerId);
        property.setManagedBy(managerId);
        property.setStatus(PropertyStatus.ACTIVE);
        propertyRepository.save(property);

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            List<Room> draftRooms = roomRepository.findByPropertyIdAndStatus(propertyId, RoomStatus.DRAFT);
            for (Room room : draftRooms) {
                room.setStatus(RoomStatus.AVAILABLE);
            }
            roomRepository.saveAll(draftRooms);
            return buildWholeHouseActivationResponse(property, propertyId);
        }
        return activatePerRoom(property, propertyId, managerId);
    }

    @Override
    @Transactional
    public PropertyResponse changeOperationManager(Long propertyId,
                                                   AssignOperationManagerRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() != PropertyStatus.ACTIVE && property.getStatus() != PropertyStatus.RENTED) {
            throw new BusinessException(
                    "Chỉ có thể đổi Operation Manager khi nhà đang ở trạng thái ACTIVE hoặc RENTED");
        }

        UUID managerId = request.getOperationManagerId();
        validateOperationManager(managerId);

        if (managerId.equals(property.getOperationManagerId())) {
            return mapPropertyResponse(property, extractShortAddress(property));
        }

        property.setOperationManagerId(managerId);
        property.setManagedBy(managerId);
        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public PropertyResponse disableProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));
        propertyDeletionService.assertNoActiveTenants(propertyId);
        if (property.getStatus() != PropertyStatus.DISABLED) {
            property.setPreviousStatus(property.getStatus());
            property.setStatus(PropertyStatus.DISABLED);
        }
        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public PropertyResponse enableProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() != PropertyStatus.DISABLED) {
            throw new BusinessException("Chỉ có thể enable khi nhà đang ở trạng thái DISABLED");
        }

        property.setStatus(property.getPreviousStatus() != null
                ? property.getPreviousStatus()
                : PropertyStatus.DRAFT);
        property.setPreviousStatus(null);
        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingSummaryResponse getOnboardingSummary(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        InboundContractResponse contractResponse = null;
        if (inboundContractRepository.existsByPropertyId(propertyId)) {
            InboundContract contract = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId).orElseThrow();
            contractResponse = InboundContractResponse.builder()
                    .id(contract.getId())
                    .propertyId(propertyId)
                    .contractCode(contract.getContractCode())
                    .ownerName(contract.getOwnerName())
                    .totalRentAmount(contract.getTotalRentAmount())
                    .startDate(contract.getStartDate())
                    .endDate(contract.getEndDate())
                    .contractScanUrl(contract.getContractScanUrl())
                    .status(contract.getStatus())
                    .build();
        }

        DepreciationCalculationResponse pricing = null;
        try {
            pricing = depreciationService.getByProperty(propertyId);
        } catch (ResourceNotFoundException ignored) {
            // chưa tính giá
        }

        return OnboardingSummaryResponse.builder()
                .propertyId(propertyId)
                .status(property.getStatus())
                .wholeHouse(property.getWholeHouse())
                .hasRenovation(property.getHasRenovation())
                .totalFloor(property.getTotalFloor())
                .totalRooms(property.getTotalRooms())
                .renovationCompleted(property.isRenovationCompleted())
                .renovationStartDate(property.getRenovationStartDate())
                .renovationEndDate(property.getRenovationEndDate())
                .submittedToHostAt(property.getSubmittedToHostAt())
                .equipmentManifest(getEquipmentManifest(propertyId))
                .renovationLines(getRenovationLines(propertyId))
                .totalRenovationCost(renovationLineRepository.sumCostByPropertyId(propertyId))
                .inboundContract(contractResponse)
                .pricing(pricing)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentCatalogResponse> listEquipmentCatalog() {
        return equipmentCatalogRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toEquipmentCatalogResponse)
                .toList();
    }

    @Override
    @Transactional
    public EquipmentCatalogResponse createEquipmentCatalog(EquipmentCatalogCreateRequest request) {
        String name = request.getName().trim();
        if (equipmentCatalogRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Tên thiết bị '" + name + "' đã tồn tại");
        }

        EquipmentCatalog saved = equipmentCatalogRepository.save(EquipmentCatalog.builder()
                .name(name)
                .description(request.getDescription())
                .active(true)
                .build());
        return toEquipmentCatalogResponse(saved);
    }

    private EquipmentCatalogResponse toEquipmentCatalogResponse(EquipmentCatalog catalog) {
        return EquipmentCatalogResponse.builder()
                .id(catalog.getId())
                .name(catalog.getName())
                .description(catalog.getDescription())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RenovationCategoryResponse> listRenovationCategories() {
        return renovationCategoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> RenovationCategoryResponse.builder()
                        .id(c.getId())
                        .code(c.getCode())
                        .name(c.getName())
                        .description(c.getDescription())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public PropertyPurgeResponse purgeProperty(Long propertyId) {
        return propertyDeletionService.purgeProperty(propertyId);
    }

    private PropertyActivationResponse confirmWholeHouse(Property property,
                                                         Long propertyId,
                                                         HostConfirmRequest request) {
        if (request.getRoomPrices() != null && !request.getRoomPrices().isEmpty()) {
            throw new BusinessException("Nhà nguyên căn không xác nhận giá theo phòng");
        }

        DepreciationResult depreciation = depreciationResultRepository.findWholeHouseByPropertyId(propertyId)
                .orElseThrow(() -> new BusinessException("Chưa có kết quả tính giá cấp nhà"));

        BigDecimal finalPrice = resolveFinalPrice(
                request.getPropertyPrice(),
                depreciation.getSuggestedMinPrice(),
                request.getContingencyPercent());

        property.setPrice(finalPrice);
        propertyRepository.save(property);

        return PropertyActivationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.WHOLE_HOUSE)
                .propertyStatus(property.getStatus())
                .propertyPrice(finalPrice)
                .adminSuggestedPrice(depreciation.getSuggestedMinPrice())
                .hostContingencyPercent(request.getContingencyPercent())
                .build();
    }

    private PropertyActivationResponse confirmPerRoom(Property property,
                                                      Long propertyId,
                                                      HostConfirmRequest request) {
        if (request.getPropertyPrice() != null) {
            throw new BusinessException("Nhà chia phòng không xác nhận giá ở cấp tòa");
        }
        if (request.getRoomPrices() == null || request.getRoomPrices().isEmpty()) {
            throw new BusinessException("Phải gửi giá từng phòng");
        }

        List<Room> draftRooms = roomRepository.findByPropertyIdAndStatus(propertyId, RoomStatus.DRAFT);
        Map<Long, HostConfirmRequest.RoomPriceConfirm> priceByRoomId = request.getRoomPrices().stream()
                .collect(Collectors.toMap(HostConfirmRequest.RoomPriceConfirm::getRoomId, Function.identity()));

        List<PropertyActivationResponse.ActivatedRoom> activatedRooms = new ArrayList<>();

        for (Room room : draftRooms) {
            HostConfirmRequest.RoomPriceConfirm priceConfirm = priceByRoomId.get(room.getId());
            if (priceConfirm == null) {
                throw new BusinessException("Thiếu giá cho phòng ID=" + room.getId());
            }

            DepreciationResult roomDepreciation = depreciationResultRepository.findByRoomId(room.getId())
                    .orElseThrow(() -> new BusinessException(
                            "Phòng " + room.getRoomNumber() + " chưa có kết quả tính giá"));

            BigDecimal finalPrice = resolveFinalPrice(
                    priceConfirm.getPrice(),
                    roomDepreciation.getSuggestedMinPrice(),
                    request.getContingencyPercent());

            room.setPrice(finalPrice);

            activatedRooms.add(PropertyActivationResponse.ActivatedRoom.builder()
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .price(finalPrice)
                    .adminSuggestedPrice(roomDepreciation.getSuggestedMinPrice())
                    .status(room.getStatus())
                    .build());
        }

        propertyRepository.save(property);
        roomRepository.saveAll(draftRooms);

        return PropertyActivationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .propertyStatus(property.getStatus())
                .hostContingencyPercent(request.getContingencyPercent())
                .rooms(activatedRooms)
                .build();
    }

    private PropertyActivationResponse buildWholeHouseActivationResponse(Property property, Long propertyId) {
        DepreciationResult depreciation = depreciationResultRepository.findWholeHouseByPropertyId(propertyId)
                .orElseThrow(() -> new BusinessException("Chưa có kết quả tính giá cấp nhà"));

        return PropertyActivationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.WHOLE_HOUSE)
                .propertyStatus(property.getStatus())
                .propertyPrice(property.getPrice())
                .adminSuggestedPrice(depreciation.getSuggestedMinPrice())
                .hostContingencyPercent(property.getHostContingencyPercent())
                .operationManagerId(property.getOperationManagerId())
                .build();
    }

    private PropertyActivationResponse buildActivePerRoomActivationResponse(Property property, Long propertyId) {
        List<PropertyActivationResponse.ActivatedRoom> rooms = roomRepository.findByPropertyId(propertyId).stream()
                .map(room -> {
                    BigDecimal adminSuggested = depreciationResultRepository.findByRoomId(room.getId())
                            .map(DepreciationResult::getSuggestedMinPrice)
                            .orElse(null);
                    return PropertyActivationResponse.ActivatedRoom.builder()
                            .roomId(room.getId())
                            .roomNumber(room.getRoomNumber())
                            .price(room.getPrice())
                            .adminSuggestedPrice(adminSuggested)
                            .status(room.getStatus())
                            .build();
                })
                .toList();

        return PropertyActivationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .propertyStatus(property.getStatus())
                .hostContingencyPercent(property.getHostContingencyPercent())
                .operationManagerId(property.getOperationManagerId())
                .rooms(rooms)
                .build();
    }

    private PropertyActivationResponse activatePerRoom(Property property, Long propertyId, UUID managerId) {
        List<Room> draftRooms = roomRepository.findByPropertyIdAndStatus(propertyId, RoomStatus.DRAFT);
        List<PropertyActivationResponse.ActivatedRoom> activatedRooms = new ArrayList<>();

        for (Room room : draftRooms) {
            if (room.getPrice() == null) {
                throw new BusinessException("Phòng " + room.getRoomNumber() + " chưa có giá — host phải xác nhận giá trước");
            }

            DepreciationResult roomDepreciation = depreciationResultRepository.findByRoomId(room.getId())
                    .orElseThrow(() -> new BusinessException(
                            "Phòng " + room.getRoomNumber() + " chưa có kết quả tính giá"));

            room.setStatus(RoomStatus.AVAILABLE);

            activatedRooms.add(PropertyActivationResponse.ActivatedRoom.builder()
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .price(room.getPrice())
                    .adminSuggestedPrice(roomDepreciation.getSuggestedMinPrice())
                    .status(room.getStatus())
                    .build());
        }

        roomRepository.saveAll(draftRooms);

        return PropertyActivationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .propertyStatus(property.getStatus())
                .hostContingencyPercent(property.getHostContingencyPercent())
                .operationManagerId(managerId)
                .rooms(activatedRooms)
                .build();
    }

    private BigDecimal resolveFinalPrice(BigDecimal override,
                                         BigDecimal suggested,
                                         BigDecimal contingencyPercent) {
        if (override != null) {
            return override;
        }
        return suggested.multiply(contingencyPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void validateReadyForSubmit(Property property) {
        Long propertyId = property.getId();

        if (property.getWholeHouse() == null || property.getHasRenovation() == null) {
            throw new BusinessException("Phải chọn loại hình thuê và có/không cải tạo trước khi gửi host");
        }
        if (!inboundContractRepository.existsByPropertyId(propertyId)) {
            throw new BusinessException("Phải ký hợp đồng inbound trước khi gửi host");
        }

        List<EquipmentManifest> manifests = equipmentManifestRepository.findByPropertyId(propertyId);
        boolean hasHandoverManifest = manifests.stream()
                .anyMatch(m -> m.getSource() == EquipmentSource.INITIAL_HANDOVER);
        boolean hasPurchasedEquipment = equipmentRepository.countByPropertyIdAndSource(
                propertyId, EquipmentSource.PURCHASED) > 0;
        boolean hasRenovationLines = !renovationLineRepository.findByPropertyId(propertyId).isEmpty();
        boolean hasHandoverDisplay = handoverEquipmentRepository.countByPropertyId(propertyId) > 0;

        if (!hasHandoverManifest && !hasPurchasedEquipment) {
            if (Boolean.TRUE.equals(property.getHasRenovation())) {
                if (!hasRenovationLines) {
                    throw new BusinessException("Phải có ít nhất một hạng mục cải tạo");
                }
            } else if (!hasHandoverDisplay) {
                // NORENO không có TB — vẫn cho phép gửi Host (chỉ tiền thuê)
            }
        }

        if (Boolean.FALSE.equals(property.getWholeHouse())) {
            for (EquipmentManifest manifest : manifests) {
                if (manifest.getSource() != EquipmentSource.INITIAL_HANDOVER) {
                    continue;
                }
                long assigned = equipmentRepository.countActiveByManifestId(manifest.getId());
                if (assigned != manifest.getQuantity()) {
                    throw new BusinessException(String.format(
                            "Thiết bị '%s' (%s): đã gán %d/%d — phải gán đủ trước khi gửi host",
                            manifest.getCatalog().getName(), manifest.getStatus(),
                            assigned, manifest.getQuantity()));
                }
            }

            long roomCount = roomRepository.countByPropertyIdAndDeletedIsFalse(propertyId);
            if (roomCount != property.getTotalRooms()) {
                throw new BusinessException(String.format(
                        "Phải tạo đủ %d phòng chi tiết (hiện có %d)", property.getTotalRooms(), roomCount));
            }
        }

        if (Boolean.TRUE.equals(property.getHasRenovation())) {
            if (renovationLineRepository.findByPropertyId(propertyId).isEmpty()) {
                throw new BusinessException("Phải có ít nhất một hạng mục cải tạo");
            }
            if (!property.isRenovationCompleted()
                    && (property.getRenovationStartDate() == null || property.getRenovationEndDate() == null)) {
                throw new BusinessException("Phải nhập lịch cải tạo (ngày bắt đầu/kết thúc)");
            }
        }
    }

    private Room resolveRoomForAssignment(Property property,
                                          Long propertyId,
                                          Long roomId,
                                          com.sep490.slms2026.enums.HouseArea houseArea) {
        if (roomId == null) {
            return null;
        }
        return roomRepository.findByIdAndPropertyId(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng/khu vực ID=" + roomId + " trong tòa nhà này"));
    }

    private void validateEquipmentPlacement(Long roomId, com.sep490.slms2026.enums.HouseArea houseArea) {
        if (roomId == null && houseArea == null) {
            throw new BusinessException("Phải chọn phòng (roomId) hoặc khu vực chung (houseArea)");
        }
        if (roomId != null && houseArea != null) {
            throw new BusinessException("Chỉ chọn một trong phòng (roomId) hoặc khu vực chung (houseArea)");
        }
    }

    private void validateEquipmentStatus(EquipmentStatus status) {
        if (status != EquipmentStatus.NEW && status != EquipmentStatus.GOOD) {
            throw new BusinessException("Trạng thái thiết bị inbound chỉ chấp nhận NEW hoặc GOOD");
        }
    }

    private void validateOperationManager(UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Operation Manager ID=" + managerId));
        if (manager.getRole() != Role.ROLE_MANAGER) {
            throw new BusinessException("User ID=" + managerId + " không phải Operation Manager");
        }
        if (manager.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Operation Manager ID=" + managerId + " chưa được kích hoạt");
        }
    }

    private Property findOnboardingProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));
        if (!property.getStatus().isOnboardingEditable()) {
            throw new BusinessException(
                    "Chỉ chỉnh sửa onboarding khi nhà đang trong quá trình nhập liệu (DRAFT → RENOVATION_COMPLETED)");
        }
        return property;
    }

    private void ensurePropertyExists(Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId);
        }
    }

    private EquipmentManifestResponse toManifestResponse(EquipmentManifest manifest) {
        long assigned = equipmentRepository.countActiveByManifestId(manifest.getId());
        return EquipmentManifestResponse.builder()
                .id(manifest.getId())
                .catalogId(manifest.getCatalog().getId())
                .catalogName(manifest.getCatalog().getName())
                .quantity(manifest.getQuantity())
                .status(manifest.getStatus())
                .source(manifest.getSource())
                .price(manifest.getPrice())
                .assignedCount(assigned)
                .build();
    }

    private RenovationLineResponse toRenovationLineResponse(RenovationLine line) {
        return RenovationLineResponse.builder()
                .id(line.getId())
                .categoryId(line.getCategory().getId())
                .categoryCode(line.getCategory().getCode())
                .categoryName(line.getCategory().getName())
                .cost(line.getCost())
                .note(line.getNote())
                .build();
    }

    private RenovationSession findOrCreateCurrentSession(Property property) {
        return renovationSessionRepository
                .findTopByPropertyIdAndEndDateIsNullOrderBySessionNumberDesc(property.getId())
                .orElseGet(() -> {
                    int nextNumber = renovationSessionRepository.findMaxSessionNumberByPropertyId(property.getId()) + 1;
                    return renovationSessionRepository.save(RenovationSession.builder()
                            .property(property)
                            .sessionNumber(nextNumber)
                            .startDate(property.getRenovationStartDate())
                            .status(RenovationSessionStatus.IN_PROGRESS)
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    private void finalizeCurrentRenovationSession(Property property, LocalDate endDate) {
        renovationSessionRepository
                .findTopByPropertyIdAndEndDateIsNullOrderBySessionNumberDesc(property.getId())
                .ifPresent(session -> {
                    session.setEndDate(endDate);
                    session.setStatus(RenovationSessionStatus.ACTIVE);
                    renovationSessionRepository.save(session);
                    if (session.getSessionNumber() >= 2) {
                        disableSupersededRenovationSessions(property.getId(), session.getId());
                    }
                });
    }

    private void disableSupersededRenovationSessions(Long propertyId, Long activeSessionId) {
        LocalDateTime now = LocalDateTime.now();
        renovationSessionRepository.findByPropertyIdAndStatus(propertyId, RenovationSessionStatus.ACTIVE)
                .stream()
                .filter(s -> !s.getId().equals(activeSessionId))
                .forEach(s -> {
                    s.setStatus(RenovationSessionStatus.DISABLED);
                    s.setDisabledAt(now);
                    renovationSessionRepository.save(s);
                });
    }

    private EquipmentResponse toEquipmentResponse(Equipment equipment) {
        Integer sessionNumber = equipment.getRenovationSession() != null
                ? equipment.getRenovationSession().getSessionNumber() : null;
        EquipmentOperationalStatus opStatus = equipment.getOperationalStatus() != null
                ? equipment.getOperationalStatus() : EquipmentOperationalStatus.ACTIVE;
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .propertyId(equipment.getProperty().getId())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .roomNumber(equipment.getRoom() != null ? equipment.getRoom().getRoomNumber() : null)
                .catalogId(equipment.getCatalog().getId())
                .catalogName(equipment.getCatalog().getName())
                .houseArea(equipment.getHouseArea())
                .source(equipment.getSource())
                .status(equipment.getStatus())
                .price(equipment.getPrice())
                .note(equipment.getNote())
                .warrantyMonths(equipment.getWarrantyMonths())
                .warrantyStartDate(equipment.getWarrantyStartDate())
                .warrantyEndDate(equipment.getWarrantyEndDate())
                .operationalStatus(opStatus.name())
                .currentEffective(opStatus == EquipmentOperationalStatus.ACTIVE)
                .renovationSessionNumber(sessionNumber)
                .renovationVersionLabel(sessionNumber != null ? "v" + sessionNumber : null)
                .disabledAt(equipment.getDisabledAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HandoverEquipmentResponse> getHandoverEquipments(Long propertyId) {
        ensurePropertyExists(propertyId);
        return handoverEquipmentRepository.findByPropertyIdOrderByIdAsc(propertyId).stream()
                .map(this::toHandoverEquipmentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentResponse> getPurchasedEquipments(Long propertyId, Integer sessionNumber) {
        ensurePropertyExists(propertyId);
        List<Equipment> equipments = sessionNumber != null
                ? equipmentRepository.findByPropertyIdAndSourceAndRenovationSession_SessionNumberOrderByIdAsc(
                propertyId, EquipmentSource.PURCHASED, sessionNumber)
                : equipmentRepository.findByPropertyIdAndSourceOrderByIdAsc(propertyId, EquipmentSource.PURCHASED);
        return equipments.stream().map(this::toEquipmentResponse).toList();
    }

    private HandoverEquipmentResponse toHandoverEquipmentResponse(HandoverEquipment equipment) {
        return HandoverEquipmentResponse.builder()
                .id(equipment.getId())
                .catalogId(equipment.getCatalog().getId())
                .catalogName(equipment.getCatalog().getName())
                .description(equipment.getDescription())
                .roomNumber(equipment.getRoomNumber())
                .houseArea(equipment.getHouseArea())
                .status(equipment.getStatus())
                .quantity(equipment.getQuantity())
                .note(equipment.getNote())
                .build();
    }

    private PropertyResponse mapPropertyResponse(Property property, String shortAddress) {
        PropertyResponse response = new PropertyResponse();
        response.setId(property.getId());
        response.setPropertyName(property.getPropertyName());
        response.setShortAddress(shortAddress);
        response.setFullAddress(property.getAddress());
        response.setZoneId(property.getZone().getId());
        response.setZoneName(property.getZone().getName());
        response.setWholeHouse(property.getWholeHouse());
        response.setHasRenovation(property.getHasRenovation());
        response.setTotalFloor(property.getTotalFloor());
        response.setTotalRooms(property.getTotalRooms());
        response.setAreaSize(property.getAreaSize());
        response.setLength(property.getLength());
        response.setWidth(property.getWidth());
        response.setStatus(property.getStatus().name());
        response.setDescriptions(property.getDescriptions());
        response.setPrice(property.getPrice());
        response.setCreatedBy(property.getCreatedBy());
        response.setOperationManagerId(property.getOperationManagerId());
        response.setRenovationCompleted(property.isRenovationCompleted());
        response.setElectricityUnitPrice(property.getElectricityUnitPrice());
        response.setWaterUnitPrice(property.getWaterUnitPrice());
        return response;
    }

    private String extractShortAddress(Property property) {
        String zoneFullName = buildZoneFullName(property.getZone());
        return property.getAddress().replace(", " + zoneFullName, "");
    }

    private String buildZoneFullName(Zone zone) {
        if (zone.getParent() != null) {
            return zone.getName() + ", " + zone.getParent().getName();
        }
        return zone.getName();
    }

    private String buildFullAddress(String shortAddress, Zone zone) {
        return shortAddress + ", " + buildZoneFullName(zone);
    }

    private void assertAddressAvailable(String fullAddress, Long excludePropertyId) {
        boolean exists = excludePropertyId == null
                ? propertyRepository.existsByAddressIgnoreCase(fullAddress)
                : propertyRepository.existsByAddressIgnoreCaseAndIdNot(fullAddress, excludePropertyId);
        if (exists) {
            throw new ConflictException("Địa chỉ này đã được sử dụng cho một tòa nhà khác");
        }
    }
}

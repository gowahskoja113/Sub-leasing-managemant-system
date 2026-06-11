package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.*;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.DepreciationService;
import com.sep490.slms2026.service.PropertyOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final RoomRepository roomRepository;
    private final InboundContractRepository inboundContractRepository;
    private final DepreciationResultRepository depreciationResultRepository;
    private final DepreciationService depreciationService;

    @Override
    @Transactional
    public PropertyResponse createDraft(PropertyDraftRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khu vực (Zone)"));

        Property property = Property.builder()
                .propertyName(request.getPropertyName())
                .address(request.getAddress() + ", " + buildZoneFullName(zone))
                .zone(zone)
                .descriptions(request.getDescriptions())
                .areaSize(request.getAreaSize())
                .totalFloor(request.getTotalFloor())
                .totalRooms(request.getTotalRooms())
                .imageUrls(request.getImageUrls())
                .status(PropertyStatus.DRAFT)
                .build();

        return mapPropertyResponse(propertyRepository.save(property), request.getAddress());
    }

    @Override
    @Transactional
    public PropertyResponse setOnboardingOptions(Long propertyId, OnboardingOptionsRequest request) {
        Property property = findEditableProperty(propertyId);
        property.setWholeHouse(request.getWholeHouse());
        property.setHasRenovation(request.getHasRenovation());

        if (!request.getHasRenovation()) {
            property.setRenovationCompleted(true);
            property.setRenovationStartDate(null);
            property.setRenovationEndDate(null);
        } else {
            property.setRenovationCompleted(false);
        }

        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public PropertyResponse updateStructure(Long propertyId, UpdatePropertyStructureRequest request) {
        Property property = findEditableProperty(propertyId);
        property.setTotalFloor(request.getTotalFloor());
        property.setTotalRooms(request.getTotalRooms());

        if (Boolean.FALSE.equals(property.getWholeHouse())) {
            long roomCount = roomRepository.countByPropertyId(propertyId);
            if (roomCount > property.getTotalRooms()) {
                throw new BusinessException(
                        "Đã có " + roomCount + " phòng — không thể giảm cấu trúc xuống "
                                + property.getTotalRooms() + " phòng. Xóa phòng thừa trước.");
            }
        }

        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public List<EquipmentManifestResponse> saveEquipmentManifest(Long propertyId,
                                                                 SaveEquipmentManifestRequest request) {
        Property property = findEditableProperty(propertyId);
        equipmentManifestRepository.deleteByPropertyId(propertyId);

        List<EquipmentManifest> saved = new ArrayList<>();
        for (EquipmentManifestItemRequest item : request.getItems()) {
            validateEquipmentStatus(item.getStatus());
            EquipmentCatalog catalog = equipmentCatalogRepository.findById(item.getCatalogId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy danh mục thiết bị ID=" + item.getCatalogId()));

            saved.add(equipmentManifestRepository.save(EquipmentManifest.builder()
                    .property(property)
                    .catalog(catalog)
                    .quantity(item.getQuantity())
                    .status(item.getStatus())
                    .build()));
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
        Property property = findEditableProperty(propertyId);
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException("Nhà nguyên căn không cần phân bố thiết bị — chỉ áp dụng cho nhà chia phòng");
        }
        validateEquipmentStatus(request.getStatus());

        EquipmentManifest manifest = equipmentManifestRepository
                .findByPropertyIdAndCatalogIdAndStatus(
                        propertyId, request.getCatalogId(), request.getStatus())
                .orElseThrow(() -> new BusinessException(String.format(
                        "Thiết bị catalog ID=%d trạng thái %s chưa có trong manifest",
                        request.getCatalogId(), request.getStatus())));

        long alreadyAssigned = equipmentRepository.countByManifestId(manifest.getId());
        if (alreadyAssigned + request.getQuantity() > manifest.getQuantity()) {
            throw new BusinessException(String.format(
                    "Catalog ID=%d (%s): đã gán %d/%d — không thể gán thêm %d",
                    request.getCatalogId(), request.getStatus(),
                    alreadyAssigned, manifest.getQuantity(), request.getQuantity()));
        }

        Room room = resolveRoomForAssignment(property, propertyId, request.getRoomId(), request.getHouseArea());

        Equipment lastSaved = null;
        for (int i = 0; i < request.getQuantity(); i++) {
            lastSaved = equipmentRepository.save(Equipment.builder()
                    .property(property)
                    .room(room)
                    .catalog(manifest.getCatalog())
                    .manifest(manifest)
                    .houseArea(request.getHouseArea())
                    .source(request.getSource())
                    .status(request.getStatus())
                    .build());
        }
        return toEquipmentResponse(lastSaved);
    }

    @Override
    @Transactional
    public RenovationLineResponse addRenovationLine(Long propertyId, AddRenovationLineRequest request) {
        Property property = findEditableProperty(propertyId);
        if (!Boolean.TRUE.equals(property.getHasRenovation())) {
            throw new BusinessException("Tòa nhà không chọn cải tạo — không thể thêm hạng mục");
        }

        RenovationCategory category = renovationCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy danh mục cải tạo ID=" + request.getCategoryId()));

        RenovationLine saved = renovationLineRepository.save(RenovationLine.builder()
                .property(property)
                .category(category)
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
    @Transactional
    public PropertyResponse setRenovationSchedule(Long propertyId, RenovationScheduleRequest request) {
        Property property = findEditableProperty(propertyId);
        if (!Boolean.TRUE.equals(property.getHasRenovation())) {
            throw new BusinessException("Tòa nhà không chọn cải tạo");
        }
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BusinessException("Ngày kết thúc cải tạo phải sau ngày bắt đầu");
        }
        property.setRenovationStartDate(request.getStartDate());
        property.setRenovationEndDate(request.getEndDate());
        property.setRenovationCompleted(false);
        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public PropertyResponse completeRenovation(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (!Boolean.TRUE.equals(property.getHasRenovation())) {
            throw new BusinessException("Tòa nhà không có cải tạo");
        }
        property.setRenovationCompleted(true);

        if (property.getStatus() == PropertyStatus.UNDER_RENOVATION) {
            property.setStatus(PropertyStatus.PENDING_HOST_REVIEW);
        }

        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional
    public OnboardingSummaryResponse submitToHost(Long propertyId) {
        Property property = findEditableProperty(propertyId);
        validateReadyForSubmit(property);

        depreciationService.calculate(propertyId, CalculateDepreciationRequest.builder().build());

        property.setSubmittedToHostAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(property.getHasRenovation()) && !property.isRenovationCompleted()) {
            property.setStatus(PropertyStatus.UNDER_RENOVATION);
        } else {
            property.setStatus(PropertyStatus.PENDING_HOST_REVIEW);
        }
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
        property.setOperationManagerId(request.getOperationManagerId());
        property.setManagedBy(request.getOperationManagerId());
        property.setStatus(PropertyStatus.ACTIVE);

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return confirmWholeHouse(property, propertyId, request);
        }
        return confirmPerRoom(property, propertyId, request);
    }

    @Override
    @Transactional
    public PropertyResponse disableProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));
        if (property.getStatus() == PropertyStatus.ACTIVE) {
            throw new BusinessException("Không thể disable nhà đang ACTIVE");
        }
        property.setStatus(PropertyStatus.DISABLED);
        return mapPropertyResponse(propertyRepository.save(property), extractShortAddress(property));
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingSummaryResponse getOnboardingSummary(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        InboundContractResponse contractResponse = null;
        if (inboundContractRepository.existsByPropertyId(propertyId)) {
            InboundContract contract = inboundContractRepository.findByPropertyId(propertyId).orElseThrow();
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
                .map(c -> EquipmentCatalogResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .build())
                .toList();
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
                .operationManagerId(request.getOperationManagerId())
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
            room.setStatus(RoomStatus.AVAILABLE);

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
                .operationManagerId(request.getOperationManagerId())
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
        if (manifests.isEmpty()) {
            throw new BusinessException("Phải khai báo manifest thiết bị trước khi gửi host");
        }
        if (Boolean.FALSE.equals(property.getWholeHouse())) {
            for (EquipmentManifest manifest : manifests) {
                long assigned = equipmentRepository.countByManifestId(manifest.getId());
                if (assigned != manifest.getQuantity()) {
                    throw new BusinessException(String.format(
                            "Thiết bị '%s' (%s): đã gán %d/%d — phải gán đủ trước khi gửi host",
                            manifest.getCatalog().getName(), manifest.getStatus(),
                            assigned, manifest.getQuantity()));
                }
            }
        }

        long roomCount = roomRepository.countByPropertyId(propertyId);
        if (roomCount != property.getTotalRooms()) {
            throw new BusinessException(String.format(
                    "Phải tạo đủ %d phòng chi tiết (hiện có %d)", property.getTotalRooms(), roomCount));
        }

        if (Boolean.TRUE.equals(property.getHasRenovation())) {
            if (renovationLineRepository.findByPropertyId(propertyId).isEmpty()) {
                throw new BusinessException("Phải có ít nhất một hạng mục cải tạo");
            }
            if (property.getRenovationStartDate() == null || property.getRenovationEndDate() == null) {
                throw new BusinessException("Phải nhập lịch cải tạo (ngày bắt đầu/kết thúc)");
            }
        }
    }

    private Room resolveRoomForAssignment(Property property,
                                          Long propertyId,
                                          Long roomId,
                                          com.sep490.slms2026.enums.HouseArea houseArea) {
        if (roomId == null) {
            throw new BusinessException("Nhà chia phòng phải gán thiết bị vào phòng (roomId)");
        }
        if (houseArea != null) {
            throw new BusinessException("Nhà chia phòng không dùng houseArea — dùng roomId");
        }
        return roomRepository.findByIdAndPropertyId(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId));
    }

    private void validateEquipmentStatus(EquipmentStatus status) {
        if (status != EquipmentStatus.NEW && status != EquipmentStatus.GOOD) {
            throw new BusinessException("Trạng thái thiết bị inbound chỉ chấp nhận NEW hoặc GOOD");
        }
    }

    private Property findEditableProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));
        if (property.getStatus() != PropertyStatus.DRAFT) {
            throw new BusinessException("Chỉ chỉnh sửa onboarding khi nhà ở trạng thái DRAFT");
        }
        return property;
    }

    private void ensurePropertyExists(Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId);
        }
    }

    private EquipmentManifestResponse toManifestResponse(EquipmentManifest manifest) {
        long assigned = equipmentRepository.countByManifestId(manifest.getId());
        return EquipmentManifestResponse.builder()
                .id(manifest.getId())
                .catalogId(manifest.getCatalog().getId())
                .catalogName(manifest.getCatalog().getName())
                .quantity(manifest.getQuantity())
                .status(manifest.getStatus())
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

    private EquipmentResponse toEquipmentResponse(Equipment equipment) {
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .propertyId(equipment.getProperty().getId())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .catalogId(equipment.getCatalog().getId())
                .catalogName(equipment.getCatalog().getName())
                .houseArea(equipment.getHouseArea())
                .source(equipment.getSource())
                .status(equipment.getStatus())
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
        response.setStatus(property.getStatus().name());
        response.setDescriptions(property.getDescriptions());
        response.setPrice(property.getPrice());
        response.setCreatedBy(property.getCreatedBy());
        response.setOperationManagerId(property.getOperationManagerId());
        response.setRenovationCompleted(property.isRenovationCompleted());
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
}

package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateViewingLeadRequest;
import com.sep490.slms2026.dto.request.ViewingLeadPropertyItemRequest;
import com.sep490.slms2026.dto.response.ViewingLeadPropertyResponse;
import com.sep490.slms2026.dto.response.ViewingLeadResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.PropertyViewingLead;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.ViewingLeadProperty;
import com.sep490.slms2026.enums.ViewingInterestType;
import com.sep490.slms2026.enums.ViewingLeadStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.PropertyViewingLeadRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.ViewingLeadService;
import com.sep490.slms2026.util.PhoneUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ViewingLeadServiceImpl implements ViewingLeadService {

    private final PropertyViewingLeadRepository leadRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ViewingLeadResponse createLead(UUID adminId, CreateViewingLeadRequest request) {
        String normalizedPhone = PhoneUtils.normalizeLocal(request.getCustomerPhone());
        validateUniqueProperties(request.getProperties());

        PropertyViewingLead lead = PropertyViewingLead.builder()
                .customerName(request.getCustomerName().trim())
                .customerPhone(normalizedPhone)
                .note(request.getNote())
                .preferredViewingAt(request.getPreferredViewingAt())
                .status(ViewingLeadStatus.NEW)
                .createdBy(adminId)
                .build();

        linkExistingUser(lead, normalizedPhone);
        lead.getInterestedProperties().addAll(buildInterestedProperties(lead, request.getProperties()));

        lead = leadRepository.save(lead);
        return toResponse(loadDetailed(lead.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ViewingLeadResponse> listLeadsForAdmin(
            String status, String phone, String keyword, Pageable pageable) {
        ViewingLeadStatus statusFilter = parseStatus(status);
        String normalizedPhone = normalizePhoneOptional(phone);

        Specification<PropertyViewingLead> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (normalizedPhone != null) {
                predicates.add(cb.equal(root.get("customerPhone"), normalizedPhone));
            }
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(root.get("customerPhone"), "%" + keyword.trim() + "%"),
                        cb.like(cb.lower(root.get("note")), pattern)
                ));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return leadRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ViewingLeadResponse getLeadForAdmin(Long id) {
        return toResponse(loadDetailed(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ViewingLeadResponse> listWishlistForCustomer(UUID userId, String userPhone, Pageable pageable) {
        return leadRepository.findForCustomer(userId, phoneVariants(userPhone), pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ViewingLeadResponse getWishlistItemForCustomer(UUID userId, String userPhone, Long leadId) {
        PropertyViewingLead lead = leadRepository.findForCustomerById(leadId, userId, phoneVariants(userPhone))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu xem nhà ID=" + leadId));
        return toResponse(lead);
    }

    private PropertyViewingLead loadDetailed(Long id) {
        return leadRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lead xem nhà ID=" + id));
    }

    private List<ViewingLeadProperty> buildInterestedProperties(
            PropertyViewingLead lead, List<ViewingLeadPropertyItemRequest> items) {
        List<ViewingLeadProperty> result = new ArrayList<>();
        for (ViewingLeadPropertyItemRequest item : items) {
            Property property = propertyRepository.findById(item.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy căn nhà ID=" + item.getPropertyId()));

            ViewingInterestType interestType = item.getInterestType();
            boolean isWholeHouseProperty = Boolean.TRUE.equals(property.getWholeHouse());
            Room room = resolveRoomForInterest(item, property, interestType, isWholeHouseProperty);

            result.add(ViewingLeadProperty.builder()
                    .lead(lead)
                    .property(property)
                    .room(room)
                    .interestType(interestType)
                    .note(item.getNote())
                    .build());
        }
        return result;
    }

    private Room resolveRoomForInterest(
            ViewingLeadPropertyItemRequest item,
            Property property,
            ViewingInterestType interestType,
            boolean isWholeHouseProperty) {
        if (interestType == ViewingInterestType.WHOLE_HOUSE) {
            if (item.getRoomId() != null) {
                throw new BusinessException(
                        "Căn \"" + property.getPropertyName() + "\" là nguyên căn — không được chọn phòng");
            }
            if (!isWholeHouseProperty) {
                throw new BusinessException(
                        "Căn \"" + property.getPropertyName() + "\" cho thuê theo phòng — phải chọn interestType=ROOM và roomId");
            }
            return null;
        }

        if (item.getRoomId() == null) {
            throw new BusinessException(
                    "Căn \"" + property.getPropertyName() + "\" cần chọn phòng cụ thể (roomId)");
        }
        if (isWholeHouseProperty) {
            throw new BusinessException(
                    "Căn \"" + property.getPropertyName() + "\" là nguyên căn — dùng interestType=WHOLE_HOUSE, không chọn phòng");
        }

        Room room = roomRepository.findById(item.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + item.getRoomId()));
        if (!room.getProperty().getId().equals(property.getId())) {
            throw new BusinessException("Phòng không thuộc căn nhà đã chọn");
        }
        return room;
    }

    private void validateUniqueProperties(List<ViewingLeadPropertyItemRequest> items) {
        Set<String> keys = new HashSet<>();
        for (ViewingLeadPropertyItemRequest item : items) {
            String roomKey = item.getInterestType() == ViewingInterestType.ROOM
                    ? String.valueOf(item.getRoomId())
                    : "whole";
            String key = item.getPropertyId() + ":" + roomKey;
            if (!keys.add(key)) {
                throw new BusinessException("Danh sách căn nhà quan tâm bị trùng lặp");
            }
        }
    }

    private void linkExistingUser(PropertyViewingLead lead, String normalizedPhone) {
        userRepository.findByPhoneNumber(normalizedPhone)
                .or(() -> userRepository.findByPhoneNumber(PhoneUtils.toInternational(normalizedPhone)))
                .ifPresent(user -> lead.setLinkedUserId(user.getId()));
    }

    private List<String> phoneVariants(String userPhone) {
        if (!StringUtils.hasText(userPhone)) {
            return List.of();
        }
        String local = PhoneUtils.normalizeLocal(userPhone);
        return List.of(local, PhoneUtils.toInternational(local));
    }

    private String normalizePhoneOptional(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return PhoneUtils.normalizeLocal(phone);
    }

    private ViewingLeadStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ViewingLeadStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái lead không hợp lệ: " + status);
        }
    }

    private ViewingLeadResponse toSummaryResponse(PropertyViewingLead lead) {
        return ViewingLeadResponse.builder()
                .id(lead.getId())
                .customerName(lead.getCustomerName())
                .customerPhone(lead.getCustomerPhone())
                .note(lead.getNote())
                .status(lead.getStatus())
                .assignedManagerId(lead.getAssignedManagerId())
                .assignedManagerName(resolveUserName(lead.getAssignedManagerId()))
                .createdBy(lead.getCreatedBy())
                .createdByName(resolveUserName(lead.getCreatedBy()))
                .linkedUserId(lead.getLinkedUserId())
                .preferredViewingAt(lead.getPreferredViewingAt())
                .scheduledAt(lead.getScheduledAt())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .propertyCount(lead.getInterestedProperties() != null ? lead.getInterestedProperties().size() : 0)
                .build();
    }

    private ViewingLeadResponse toResponse(PropertyViewingLead lead) {
        ViewingLeadResponse response = toSummaryResponse(lead);
        response.setProperties(lead.getInterestedProperties().stream()
                .map(this::toPropertyResponse)
                .toList());
        return response;
    }

    private ViewingLeadPropertyResponse toPropertyResponse(ViewingLeadProperty item) {
        Property property = item.getProperty();
        Room room = item.getRoom();
        return ViewingLeadPropertyResponse.builder()
                .id(item.getId())
                .propertyId(property.getId())
                .propertyName(property.getPropertyName())
                .propertyAddress(property.getAddress())
                .propertyStatus(property.getStatus() != null ? property.getStatus().name() : null)
                .propertyWholeHouse(property.getWholeHouse())
                .propertyPrice(property.getPrice())
                .propertyImageUrls(property.getImageUrls())
                .interestType(item.getInterestType())
                .roomId(room != null ? room.getId() : null)
                .roomNumber(room != null ? room.getRoomNumber() : null)
                .roomFloor(room != null ? room.getFloor() : null)
                .roomPrice(room != null ? room.getPrice() : null)
                .note(item.getNote())
                .build();
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(u -> u.getFullName()).orElse(null);
    }
}

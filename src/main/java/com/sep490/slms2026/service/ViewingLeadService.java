package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateViewingLeadRequest;
import com.sep490.slms2026.dto.response.ViewingLeadResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ViewingLeadService {

    ViewingLeadResponse createLead(UUID adminId, CreateViewingLeadRequest request);

    Page<ViewingLeadResponse> listLeadsForAdmin(String status, String phone, String keyword, Pageable pageable);

    ViewingLeadResponse getLeadForAdmin(Long id);

    Page<ViewingLeadResponse> listWishlistForCustomer(UUID userId, String userPhone, Pageable pageable);

    ViewingLeadResponse getWishlistItemForCustomer(UUID userId, String userPhone, Long leadId);
}

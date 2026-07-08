package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.ViewingLeadResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.ViewingLeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/viewing-wishlist")
@RequiredArgsConstructor
public class ViewingWishlistController {

    private final ViewingLeadService viewingLeadService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'TENANT')")
    public Page<ViewingLeadResponse> listWishlist(Pageable pageable) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return viewingLeadService.listWishlistForCustomer(user.getId(), user.getPhoneNumber(), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TENANT')")
    public ViewingLeadResponse getWishlistItem(@PathVariable Long id) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return viewingLeadService.getWishlistItemForCustomer(user.getId(), user.getPhoneNumber(), id);
    }
}

package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.GuestPropertyResponse;
import com.sep490.slms2026.dto.response.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PublicPropertyService {

    Page<GuestPropertyResponse> listPublicProperties(Pageable pageable);

    GuestPropertyResponse getPublicProperty(Long id);

    List<RoomResponse> getPublicRooms(Long propertyId);
}

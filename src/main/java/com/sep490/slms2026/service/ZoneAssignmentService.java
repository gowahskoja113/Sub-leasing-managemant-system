package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.AssignZoneManagerRequest;
import com.sep490.slms2026.dto.request.ManagerTransferRequest;
import com.sep490.slms2026.dto.response.IdleManagerResponse;
import com.sep490.slms2026.dto.response.ZoneAssignmentHistoryResponse;
import com.sep490.slms2026.dto.response.ZoneAssignmentResponse;
import com.sep490.slms2026.dto.response.ZoneHandoverResponse;

import java.util.List;
import java.util.UUID;

public interface ZoneAssignmentService {

    List<ZoneAssignmentResponse> getAllAssignments();

    ZoneHandoverResponse assignManager(UUID zoneId, AssignZoneManagerRequest request);

    void removeManager(UUID zoneId);

    ZoneHandoverResponse transferManager(ManagerTransferRequest request);

    List<IdleManagerResponse> listIdleManagers();

    List<ZoneHandoverResponse> getZoneHandovers(UUID zoneId);

    List<ZoneAssignmentHistoryResponse> getUserAssignmentHistory(UUID userId);

}

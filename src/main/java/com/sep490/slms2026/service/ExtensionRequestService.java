package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateExtensionRequest;
import com.sep490.slms2026.dto.request.ExtensionNoteRequest;
import com.sep490.slms2026.dto.request.RejectExtensionRequest;
import com.sep490.slms2026.dto.response.ExtensionOptionsResponse;
import com.sep490.slms2026.dto.response.ExtensionRequestResponse;

import java.util.List;
import java.util.UUID;

public interface ExtensionRequestService {
    ExtensionOptionsResponse getExtensionOptions(UUID tenantUserId, Long contractId);
    ExtensionRequestResponse createRequest(UUID tenantUserId, Long contractId, CreateExtensionRequest request);
    ExtensionRequestResponse withdrawRequest(UUID tenantUserId, Long requestId);
    List<ExtensionRequestResponse> listRequestsForTenant(UUID tenantUserId, Long contractId);
    
    List<ExtensionRequestResponse> listRequestsForManager(String status);
    ExtensionRequestResponse addManagerNote(UUID managerUserId, Long requestId, ExtensionNoteRequest request);
    
    List<ExtensionRequestResponse> listRequestsForAdmin(String status);
    ExtensionRequestResponse approveRequest(UUID adminUserId, Long requestId);
    ExtensionRequestResponse rejectRequest(UUID adminUserId, Long requestId, RejectExtensionRequest request);
}

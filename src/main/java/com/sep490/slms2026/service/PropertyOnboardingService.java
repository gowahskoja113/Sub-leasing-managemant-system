package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.*;

import java.util.List;

public interface PropertyOnboardingService {

    PropertyResponse createDraft(PropertyDraftRequest request);

    PropertyResponse setOnboardingOptions(Long propertyId, OnboardingOptionsRequest request);

    PropertyResponse updateStructure(Long propertyId, UpdatePropertyStructureRequest request);

    List<EquipmentManifestResponse> saveEquipmentManifest(Long propertyId, SaveEquipmentManifestRequest request);

    List<EquipmentManifestResponse> getEquipmentManifest(Long propertyId);

    EquipmentResponse assignEquipment(Long propertyId, AssignEquipmentRequest request);

    RenovationLineResponse addRenovationLine(Long propertyId, AddRenovationLineRequest request);

    List<RenovationLineResponse> getRenovationLines(Long propertyId);

    List<RenovationSessionResponse> getRenovationSessions(Long propertyId);

    PropertyResponse setRenovationSchedule(Long propertyId, RenovationScheduleRequest request);

    PropertyResponse startRenovation(Long propertyId);

    PropertyResponse completeRenovation(Long propertyId);

    OnboardingSummaryResponse submitToHost(Long propertyId);

    PropertyActivationResponse hostConfirm(Long propertyId, HostConfirmRequest request);

    PropertyActivationResponse assignOperationManager(Long propertyId, AssignOperationManagerRequest request);

    PropertyResponse changeOperationManager(Long propertyId, AssignOperationManagerRequest request);

    PropertyResponse disableProperty(Long propertyId);

    OnboardingSummaryResponse getOnboardingSummary(Long propertyId);

    List<EquipmentCatalogResponse> listEquipmentCatalog();

    EquipmentCatalogResponse createEquipmentCatalog(EquipmentCatalogCreateRequest request);

    List<RenovationCategoryResponse> listRenovationCategories();

    PropertyPurgeResponse purgeProperty(Long propertyId);
}

package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.PropertyPurgeResponse;

public interface PropertyDeletionService {

    PropertyPurgeResponse purgeProperty(Long propertyId);
}

package com.sep490.slms2026.imports;

import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RenovationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RenovationPhaseSupport {

    private final RenovationSessionRepository renovationSessionRepository;
    private final PropertyRepository propertyRepository;
    private final InboundContractRepository inboundContractRepository;

    /**
     * Cải tạo bổ sung: đã gọi {@code startRenovation} — session đang mở từ lần thứ 2 trở đi.
     */
    public boolean isSupplementRenovationPhase(Long propertyId) {
        return renovationSessionRepository
                .findTopByPropertyIdAndEndDateIsNullOrderBySessionNumberDesc(propertyId)
                .map(session -> session.getSessionNumber() >= 2)
                .orElse(false);
    }

    public boolean isOnboardingRenovationPhase(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return false;
        }
        if (property.getStatus() != PropertyStatus.UNDER_RENOVATION) {
            return false;
        }
        return !isSupplementRenovationPhase(propertyId);
    }

    public Property findPropertyByContractCode(String contractCode) {
        return inboundContractRepository.findByContractCode(contractCode)
                .orElseThrow()
                .getProperty();
    }
}

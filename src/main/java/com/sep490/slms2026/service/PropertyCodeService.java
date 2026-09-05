package com.sep490.slms2026.service;

import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ConflictException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.util.PropertyCodeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PropertyCodeService {

    private final PropertyRepository propertyRepository;

    /**
     * Gán mã nhà khi tạo mới. Client có thể gửi {@code propertyCode}; nếu không thì tự sinh.
     */
    public String resolveForCreate(String requestedCode, String propertyName) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String normalized = PropertyCodeHelper.normalize(requestedCode);
            assertValidFormat(normalized);
            assertUnique(normalized, null);
            return normalized;
        }

        String fromName = PropertyCodeHelper.extractFromPropertyName(propertyName);
        if (fromName != null) {
            return allocateUnique(fromName, null);
        }
        return generateNextMtxCode(null);
    }

    private String allocateUnique(String base, Long excludePropertyId) {
        String candidate = base;
        int suffix = 2;
        while (isTaken(candidate, excludePropertyId)) {
            candidate = PropertyCodeHelper.withCollisionSuffix(base, suffix++);
            if (suffix > 999) {
                return generateNextMtxCode(excludePropertyId);
            }
        }
        return candidate;
    }

    private String generateNextMtxCode(Long excludePropertyId) {
        int max = propertyRepository.findAllPropertyCodes().stream()
                .mapToInt(PropertyCodeHelper::parseMtxNumber)
                .max()
                .orElse(0);
        String candidate;
        do {
            max++;
            candidate = PropertyCodeHelper.formatMtxCode(max);
        } while (isTaken(candidate, excludePropertyId));
        return candidate;
    }

    private void assertValidFormat(String code) {
        if (!PropertyCodeHelper.isValidFormat(code)) {
            throw new BusinessException(
                    "Mã nhà chỉ được chứa chữ, số và ký tự # - _, tối đa "
                            + PropertyCodeHelper.MAX_LENGTH + " ký tự");
        }
    }

    private void assertUnique(String code, Long excludePropertyId) {
        if (isTaken(code, excludePropertyId)) {
            throw new ConflictException("Mã nhà \"" + code + "\" đã được sử dụng");
        }
    }

    private boolean isTaken(String code, Long excludePropertyId) {
        return excludePropertyId == null
                ? propertyRepository.existsByPropertyCode(code)
                : propertyRepository.existsByPropertyCodeAndIdNot(code, excludePropertyId);
    }
}

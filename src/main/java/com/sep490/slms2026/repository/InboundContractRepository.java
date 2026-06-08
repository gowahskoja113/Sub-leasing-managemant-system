package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InboundContract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundContractRepository extends JpaRepository<InboundContract, Long> {

    boolean existsByPropertyId(Long propertyId);

    java.util.Optional<InboundContract> findByPropertyId(Long propertyId);
}

package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.RenovationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RenovationLineRepository extends JpaRepository<RenovationLine, Long> {

    List<RenovationLine> findByPropertyId(Long propertyId);

    void deleteByPropertyId(Long propertyId);

    @Query("SELECT COALESCE(SUM(r.cost), 0) FROM RenovationLine r WHERE r.property.id = :propertyId")
    BigDecimal sumCostByPropertyId(@Param("propertyId") Long propertyId);
}

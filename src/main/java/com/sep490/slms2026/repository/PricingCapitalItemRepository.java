package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.PricingCapitalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingCapitalItemRepository extends JpaRepository<PricingCapitalItem, Long> {
    List<PricingCapitalItem> findByPropertyId(Long propertyId);
    List<PricingCapitalItem> findByPropertyIdAndPricingVersion(@Param("propertyId") Long propertyId, @Param("pricingVersion") Integer pricingVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PricingCapitalItem p WHERE p.propertyId = :propertyId AND p.pricingVersion = :pricingVersion")
    void deleteByPropertyIdAndPricingVersion(@Param("propertyId") Long propertyId, @Param("pricingVersion") Integer pricingVersion);

    void deleteByPropertyId(Long propertyId);
}

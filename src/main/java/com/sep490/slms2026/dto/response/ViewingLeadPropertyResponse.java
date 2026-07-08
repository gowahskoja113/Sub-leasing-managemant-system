package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.ViewingInterestType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ViewingLeadPropertyResponse {
    private Long id;
    private Long propertyId;
    private String propertyName;
    private String propertyAddress;
    private String propertyStatus;
    private Boolean propertyWholeHouse;
    private BigDecimal propertyPrice;
    private List<String> propertyImageUrls;
    private ViewingInterestType interestType;
    private Long roomId;
    private String roomNumber;
    private Integer roomFloor;
    private BigDecimal roomPrice;
    private String note;
}

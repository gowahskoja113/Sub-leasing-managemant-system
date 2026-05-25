package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.GeminiParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PropertySearchService {

    @Autowired
    private GeminiParserService geminiParserService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private RoomRepository roomRepository;

    public Map<String, Object> searchByAi(String userPrompt) {
        Map<String, Object> criteria = geminiParserService.parseUserPrompt(userPrompt);
        if (criteria.isEmpty()) return Map.of("message", "Không thể phân tích yêu cầu");

        String type = criteria.get("type") != null ? criteria.get("type").toString() : "ALL";

        // TRƯỜNG HỢP 1: Khách muốn tìm phòng đơn / phòng trọ lẻ (ROOM)
        if ("ROOM".equals(type)) {
            Specification<Room> roomSpec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                // Tìm theo giá phòng
                if (criteria.get("maxPrice") != null) {
                    Double maxPrice = Double.valueOf(criteria.get("maxPrice").toString());
                    predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice)); // Giả định cột giá ở bảng Room tên là price
                }
                // Tìm kiếm theo từ khóa ở phần mô tả phòng
                if (criteria.get("keyword") != null) {
                    String keyword = criteria.get("keyword").toString();
                    predicates.add(cb.like(cb.lower(root.get("description")), "%" + keyword.toLowerCase() + "%"));
                }
                // Tìm kiếm thông qua mối quan hệ với bảng Zone hoặc Property nếu cần thiết bằng Join...

                return cb.and(predicates.toArray(new Predicate[0]));
            };
            List<Room> rooms = roomRepository.findAll(roomSpec);
            return Map.of("searchType", "ROOM", "data", rooms);
        }

        // TRƯỜNG HỢP 2: Khách muốn tìm nhà nguyên căn hoặc mặc định (PROPERTY)
        else {
            Specification<Property> propertySpec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                // Tìm theo giá nhà nguyên căn
                if (criteria.get("maxPrice") != null) {
                    Double maxPrice = Double.valueOf(criteria.get("maxPrice").toString());
                    predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice)); // Giả định cột giá ở bảng Property tên là price
                }
                // Tìm theo khu vực (Zone) - Giả định có mối quan hệ liên kết với Entity Zone
                if (criteria.get("zone") != null) {
                    String zoneName = criteria.get("zone").toString();
                    // Join sang entity Zone để lấy tên tìm kiếm
                    predicates.add(cb.like(cb.lower(root.get("zone").get("name")), "%" + zoneName.toLowerCase() + "%"));
                }
                // Tìm kiếm theo từ khóa tiện ích, nội thất
                if (criteria.get("keyword") != null) {
                    String keyword = criteria.get("keyword").toString();
                    predicates.add(cb.like(cb.lower(root.get("description")), "%" + keyword.toLowerCase() + "%"));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };
            List<Property> properties = propertyRepository.findAll(propertySpec);
            return Map.of("searchType", "PROPERTY", "data", properties);
        }
    }
}
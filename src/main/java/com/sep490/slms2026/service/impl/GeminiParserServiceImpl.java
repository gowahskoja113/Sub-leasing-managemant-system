package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.service.GeminiParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiParserServiceImpl implements GeminiParserService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> parseUserPrompt(String userPrompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + apiKey;

        String systemInstruction = """
            Bạn là bộ phân tích dữ liệu tìm kiếm hệ thống quản lý nhà và phòng cho thuê.
            Nhiệm vụ của bạn là đọc câu yêu cầu của khách hàng và trích xuất thành các tiêu chí tìm kiếm dưới dạng JSON.
            
            Cấu trúc JSON bắt buộc phải tuân theo mẫu sau (nếu không có thông tin nào thì để null hoặc trống):
            {
              "zone": "tên khu vực hoặc quận/huyện tại TP.HCM (ví dụ: Quận 7, Bình Chánh,...)",
              "type": "PROPERTY" hoặc "ROOM" hoặc null (PROPERTY nếu họ muốn thuê nhà nguyên căn/cả căn, ROOM nếu họ muốn thuê phòng đơn/phòng trọ),
              "maxPrice": số_nguyên_giá_tối_đa (đơn vị VNĐ, ví dụ 5 triệu thì là 5000000),
              "keyword": "từ khóa đặc biệt như đầy đủ nội thất, gần công ty, hẻm lớn,..."
            }
            
            CHÚ Ý QUAN TRỌNG: Chỉ trả ra duy nhất chuỗi JSON thô, không bọc trong ký tự ```json ... ```, không thêm bất kỳ lời giải thích nào khác.
            """;

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt))))
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String rawJsonResult = parts.get(0).get("text").toString().trim();

            return objectMapper.readValue(rawJsonResult, Map.class);
        } catch (Exception e) {
            System.err.println("Lỗi phân tích Prompt: " + e.getMessage());
            return Map.of();
        }
    }
}

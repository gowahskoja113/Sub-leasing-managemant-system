package com.sep490.slms2026.config;

import lombok.Getter;
import lombok.Setter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Chỉ còn dùng {@code signPlace} cho dòng ngày ký. Thông tin Bên cho thuê hard-code trong file Word. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.contract.lessor")
public class ContractLessorProperties {

    private String signPlace = "TP. HCM";

    // Các field dưới không còn map vào template — giữ để tương thích config cũ nếu có.
    private String name = "";
    private String idNumber = "";
    private String address = "";
    private String phone = "";
    private String bankAccount = "";
    private String bankName = "";
}

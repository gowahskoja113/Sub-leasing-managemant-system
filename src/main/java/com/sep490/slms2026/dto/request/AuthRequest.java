package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthRequest {
    @NotBlank(message = "Username không được để trống")
    private String username;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 6, message = "Password phải từ 6 ký tự trở lên")
    private String password;

    private String phoneNumber; // Dùng thêm khi Đăng ký
    private String role;        // Dùng thêm khi Đăng ký (Ví dụ: ROLE_TENANT)
}
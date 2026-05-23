package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.AuthRequest;
import com.sep490.slms2026.dto.response.AuthResponse;

public interface AuthService {
    String register(AuthRequest request);
    AuthResponse login(AuthRequest request);
}
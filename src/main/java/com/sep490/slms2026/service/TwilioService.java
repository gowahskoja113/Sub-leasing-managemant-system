package com.sep490.slms2026.service;

public interface TwilioService {

    boolean isConfigured();

    void sendOtp(String toPhoneNumber, String code);
}

package com.sep490.slms2026.service;

public interface TwilioService {

    boolean isConfigured();

    void sendSms(String toPhoneNumber, String message);
}

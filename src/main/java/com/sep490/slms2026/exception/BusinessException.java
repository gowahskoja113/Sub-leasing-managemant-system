package com.sep490.slms2026.exception;

public class BusinessException extends RuntimeException {
    private String code = "BUSINESS_ERROR";

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

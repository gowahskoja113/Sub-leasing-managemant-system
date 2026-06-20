package com.sep490.slms2026.exception;

import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class BulkImportValidationException extends RuntimeException {

    private final List<BulkImportErrorResponse> errors;

    public BulkImportValidationException(String message, List<BulkImportErrorResponse> errors) {
        super(message);
        this.errors = errors;
    }
}

package com.fieldnotes.common.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorItem> details
) {
    public record FieldErrorItem(String field, String message) {}
}

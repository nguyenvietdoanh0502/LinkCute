package com.hadilao.be.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ==========================================
    // SYSTEM & GENERIC ERRORS
    // ==========================================
    UNCATEGORIZED_EXCEPTION("UNCATEGORIZED_EXCEPTION", "Uncategorized exception", 500),
    INVALID_KEY("INVALID_KEY", "Uncategorized exception", 400),

    // ==========================================
    // AUTHENTICATION & AUTHORIZATION ERRORS
    // ==========================================
    UNAUTHENTICATED("UNAUTHENTICATED", "Unauthenticated", 401),
    UNAUTHORIZED("UNAUTHORIZED", "You do not have permission", 403),
    ACCOUNT_NOT_VERIFIED("ACCOUNT_NOT_VERIFIED","Account is not verified", 403),
    ACCOUNT_BANNED("ACCOUNT_BANNED","Account has been banned", 403),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS","Invalid credentials", 401),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED","Your account has been locked, try again in 15 minutes", 423),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "Invalid or expired refresh token", 401),

    // ==========================================
    // VALIDATION & INPUT ERRORS (400)
    // ==========================================
    MISSING_EMAIL("MISSING_EMAIL", "Email is required", 400),
    INVALID_EMAIL("INVALID_EMAIL", "Email is not valid", 400),

    MISSING_PASSWORD("MISSING_PASSWORD", "Password is required", 400),
    INVALID_PASSWORD("INVALID_PASSWORD", "Password must be at least 8 characters", 400),
    WEAK_PASSWORD("WEAK_PASSWORD", "Password must be at least 8 characters, contain at least one uppercase letter, one lowercase letter, one number, and one special character", 400),

    MISSING_CONFIRM_PASSWORD("MISSING_CONFIRM_PASSWORD", "Confirm Password is required", 400),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", "Passwords do not match", 400),

    MISSING_FULL_NAME("MISSING_FULL_NAME", "Full name is required", 400),
    INVALID_FULL_NAME("INVALID_FULL_NAME", "Full name must be between 2 and 100 characters", 400),

    MISSING_OTP("MISSING_OTP", "OTP is required", 400),
    INVALID_OTP_FORMAT("INVALID_OTP_FORMAT", "OTP must be exactly 6 digits", 400),

    USERNAME_INVALID("USERNAME_INVALID", "Username must be at least 3 characters", 400),
    INVALID_DOB("INVALID_DOB", "Your age must be at least {min}", 400),

    // ==========================================
    // BUSINESS LOGIC ERRORS
    // ==========================================
    USER_EXISTED("USER_EXISTED", "User existed", 409),
    USER_NOT_EXISTED("USER_NOT_EXISTED", "User not existed", 404),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Resource not found", 404),
    CONCURRENCY_CONFLICT("CONCURRENCY_CONFLICT", "Resource is being processed by another user", 409),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Rate limit exceeded", 429),

    // ==========================================
    // PLACE ERRORS
    // ==========================================
    INVALID_INPUT("INVALID_INPUT", "Invalid input format", 400),
    PLACE_DATA_IMPORT_FAILED("PLACE_DATA_IMPORT_FAILED", "Could not import place data", 500),

    // ==========================================
    // PASSWORD MANAGEMENT ERRORS
    // ==========================================
    OTP_EXPIRED_OR_INVALID("OTP_EXPIRED_OR_INVALID", "OTP code has expired or is invalid", 400),
    OTP_INVALID("OTP_INVALID", "Invalid OTP code", 400),
    INVALID_OLD_PASSWORD("INVALID_OLD_PASSWORD", "Current password is incorrect", 400),
    MISSING_RESET_TOKEN("MISSING_RESET_TOKEN", "Reset token is required", 400),
    INVALID_RESET_TOKEN("INVALID_RESET_TOKEN", "Reset token is invalid or expired", 400),
    SAME_PASSWORD("SAME_PASSWORD", "New password must be different from current password", 400);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}

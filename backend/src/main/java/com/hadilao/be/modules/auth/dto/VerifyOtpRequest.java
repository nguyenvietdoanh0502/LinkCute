package com.hadilao.be.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "MISSING_EMAIL")
    @Email(message = "INVALID_EMAIL")
    private String email;

    @NotBlank(message = "MISSING_OTP")
    @Size(min = 6, max = 6, message = "INVALID_OTP_FORMAT")
    @Pattern(regexp = "^\\d{6}$", message = "INVALID_OTP_FORMAT")
    private String otpCode;
}

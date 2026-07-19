package com.hadilao.be.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "MISSING_EMAIL")
    @Email(message = "INVALID_EMAIL")
    private String email;

    @NotBlank(message = "MISSING_RESET_TOKEN")
    @Size(max = 2048, message = "INVALID_RESET_TOKEN")
    @Pattern(
            regexp = "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$",
            message = "INVALID_RESET_TOKEN"
    )
    private String resetToken;
    @NotBlank(message = "MISSING_PASSWORD")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$", message = "WEAK_PASSWORD")
    private String newPassword;

    @NotBlank(message = "MISSING_CONFIRM_PASSWORD")
    private String confirmNewPassword;
}

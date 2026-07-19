package com.hadilao.be.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;


@Data
public class ChangePasswordRequest {
    @NotBlank(message = "MISSING_PASSWORD")
    private String oldPassword;

    @NotBlank(message = "MISSING_PASSWORD")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$", message = "WEAK_PASSWORD")
    private String newPassword;

    @NotBlank(message = "MISSING_CONFIRM_PASSWORD")
    private String confirmNewPassword;
}

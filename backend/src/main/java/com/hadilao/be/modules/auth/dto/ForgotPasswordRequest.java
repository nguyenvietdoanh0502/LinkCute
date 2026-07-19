package com.hadilao.be.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "MISSING_EMAIL")
    @Email(message = "INVALID_EMAIL")
    private String email;
}

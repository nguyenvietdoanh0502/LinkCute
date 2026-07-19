package com.hadilao.be.modules.auth.dto;

import com.hadilao.be.core.common.validation.FieldsValueMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@FieldsValueMatch(
        field = "password",
        fieldMatch = "confirmPassword",
        message = "PASSWORD_MISMATCH"
)
public class RegisterRequest {
    @NotBlank(message = "MISSING_EMAIL")
    @Email(message = "INVALID_EMAIL")
    private String email;

    @NotBlank(message = "MISSING_PASSWORD")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$", message = "WEAK_PASSWORD")
    private String password;
    
    @NotBlank(message = "MISSING_CONFIRM_PASSWORD")
    private String confirmPassword;

    @NotBlank(message = "MISSING_FULL_NAME")
    @Size(min = 2, max = 100, message = "INVALID_FULL_NAME")
    private String fullName;
}

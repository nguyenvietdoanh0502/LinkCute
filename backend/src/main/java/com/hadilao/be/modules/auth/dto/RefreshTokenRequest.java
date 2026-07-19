package com.hadilao.be.modules.auth.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "INVALID_REFRESH_TOKEN")
    private String refreshToken;
}

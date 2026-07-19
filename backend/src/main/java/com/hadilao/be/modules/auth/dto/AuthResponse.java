package com.hadilao.be.modules.auth.dto;

import com.hadilao.be.modules.user.dto.UserDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private UserDTO user;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
}

package com.hadilao.be.modules.user.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class UserDTO {
    private UUID id;
    private String email;
    private String fullName;
    private String pinCode;
    private String avatarUrl;
}

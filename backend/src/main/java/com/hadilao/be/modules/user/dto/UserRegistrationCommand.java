package com.hadilao.be.modules.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRegistrationCommand {
    private String email;
    private String hashedPassword;
    private String fullName;
}

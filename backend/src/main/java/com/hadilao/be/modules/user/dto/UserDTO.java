package com.hadilao.be.modules.user.dto;

import com.hadilao.be.modules.user.enums.Gender;
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
    private Gender gender;
    private Integer birthYear;
    private Integer age;
    private String address;
}

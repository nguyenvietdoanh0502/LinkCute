package com.hadilao.be.modules.user.service;

import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.Gender;

import java.time.Year;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserDTO toDTO(User user) {
        Integer birthYear = user.getBirthYear();
        Integer age = birthYear == null ? null : Year.now().getValue() - birthYear;
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .pinCode(user.getPinCode())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender() == null ? Gender.UNSPECIFIED : user.getGender())
                .birthYear(birthYear)
                .age(age)
                .address(user.getAddress())
                .build();
    }
}

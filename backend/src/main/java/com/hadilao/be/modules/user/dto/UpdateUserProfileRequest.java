package com.hadilao.be.modules.user.dto;

import com.hadilao.be.modules.user.enums.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    @NotBlank(message = "MISSING_FULL_NAME")
    @Size(min = 2, max = 100, message = "INVALID_FULL_NAME")
    private String fullName;

    private Gender gender;

    @Min(value = 1900, message = "INVALID_BIRTH_YEAR")
    @Max(value = 2100, message = "INVALID_BIRTH_YEAR")
    private Integer birthYear;

    @Size(max = 255, message = "INVALID_ADDRESS")
    private String address;
}

package com.hadilao.be.modules.user.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.user.dto.UpdateUserProfileRequest;
import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping(UrlConstant.User.ME)
    public ResponseEntity<ApiResponse<UserDTO>> getCurrentProfile() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getCurrentProfile()));
    }

    @PatchMapping(UrlConstant.User.PROFILE)
    public ResponseEntity<ApiResponse<UserDTO>> updateCurrentProfile(
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated",
                userProfileService.updateCurrentProfile(request)));
    }

    @PostMapping(value = UrlConstant.User.AVATAR, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserDTO>> updateAvatar(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Avatar updated",
                userProfileService.updateAvatar(file)));
    }

    @DeleteMapping(UrlConstant.User.AVATAR)
    public ResponseEntity<ApiResponse<UserDTO>> removeAvatar() {
        return ResponseEntity.ok(ApiResponse.success(
                "Avatar removed",
                userProfileService.removeAvatar()));
    }
}

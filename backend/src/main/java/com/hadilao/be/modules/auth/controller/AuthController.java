package com.hadilao.be.modules.auth.controller;

import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.common.utils.IpUtils;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.dto.*;
import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(UrlConstant.Auth.REGISTER)
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest) {
        authService.register(request, IpUtils.getClientIpAddress(httpServletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("OTP has been sent to your email", null));
    }

    @PostMapping(UrlConstant.Auth.REFRESH_TOKEN)
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToke(@Valid @RequestBody RefreshTokenRequest request){
        RefreshTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    @PostMapping(UrlConstant.Auth.LOGIN)
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request){
        String ipAddress = IpUtils.getClientIpAddress(request);
        AuthResponse response = authService.login(loginRequest,ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Login successful",response));
    }
    @PostMapping(UrlConstant.Auth.VERIFY_OTP)
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpServletRequest) {
        AuthResponse response = authService.verifyOtp(request, IpUtils.getClientIpAddress(httpServletRequest));
        return ResponseEntity.ok(ApiResponse.success("Verification successful", response));
    }
    @PostMapping(UrlConstant.Auth.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request){
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
    @PostMapping(UrlConstant.Auth.CHANGE_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request){
        if(!Objects.equals(request.getConfirmNewPassword(), request.getNewPassword())){
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }
        if(Objects.equals(request.getOldPassword(), request.getNewPassword())){
            throw new AppException(ErrorCode.SAME_PASSWORD);
        }
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
    @PostMapping(UrlConstant.Auth.FORGOT_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpServletRequest){
        authService.forgotPassword(request, IpUtils.getClientIpAddress(httpServletRequest));
        return ResponseEntity.ok(ApiResponse.success("If the email is registered, you will receive an OTP code to reset your password.",null));
    }
    @PostMapping(UrlConstant.Auth.VERIFY_OTP_FORGOT_PASSWORD)
    public ResponseEntity<ApiResponse<VerifyOtpForgotPasswordResponse>> verifyOtpForgotPassword(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpServletRequest){
        VerifyOtpForgotPasswordResponse response = authService.verifyOtpForgotPassword(
                request, IpUtils.getClientIpAddress(httpServletRequest));
        return ResponseEntity.ok(ApiResponse.success("Verification successful", response));
    }
    @PostMapping(UrlConstant.Auth.RESET_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request){
        if(!Objects.equals(request.getNewPassword(), request.getConfirmNewPassword())){
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Change password successful",null));
    }
}

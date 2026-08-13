package com.hadilao.be.modules.auth.controller;

import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.common.utils.IpUtils;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.dto.*;
import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.auth.service.AuthService;
import com.hadilao.be.modules.auth.service.AuthRequestSecurityPolicy;
import com.hadilao.be.modules.auth.service.RefreshTokenCookieService;
import com.hadilao.be.modules.auth.service.RefreshTokenRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final AuthRequestSecurityPolicy authRequestSecurityPolicy;

    @PostMapping(UrlConstant.Auth.REGISTER)
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest) {
        authService.register(request, IpUtils.getClientIpAddress(httpServletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("OTP has been sent to your email", null));
    }

    @PostMapping(UrlConstant.Auth.REFRESH_TOKEN)
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @CookieValue(name = RefreshTokenCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse httpServletResponse) {
        authRequestSecurityPolicy.validate(request);
        try {
            AuthService.TokenRefreshResult result = authService.refreshToken(refreshToken);
            refreshTokenCookieService.write(httpServletResponse, result.refreshToken());
            return tokenResponse(ApiResponse.success(result.response()));
        } catch (RefreshTokenRejectedException exception) {
            if (exception.shouldClearCookie()) {
                refreshTokenCookieService.clear(httpServletResponse);
            }
            throw exception;
        }
    }
    @PostMapping(UrlConstant.Auth.LOGIN)
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse httpServletResponse){
        authRequestSecurityPolicy.validate(request);
        String ipAddress = IpUtils.getClientIpAddress(request);
        AuthService.AuthenticationResult result = authService.login(loginRequest,ipAddress);
        refreshTokenCookieService.write(httpServletResponse, result.refreshToken());
        return tokenResponse(ApiResponse.success("Login successful",result.response()));
    }
    @PostMapping(UrlConstant.Auth.VERIFY_OTP)
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        authRequestSecurityPolicy.validate(httpServletRequest);
        AuthService.AuthenticationResult result = authService.verifyOtp(
                request, IpUtils.getClientIpAddress(httpServletRequest));
        refreshTokenCookieService.write(httpServletResponse, result.refreshToken());
        return tokenResponse(ApiResponse.success("Verification successful", result.response()));
    }
    @PostMapping(UrlConstant.Auth.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response){
        authRequestSecurityPolicy.validate(request);
        try {
            authService.logout(refreshToken, bearerToken(request));
            return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
        } finally {
            refreshTokenCookieService.clear(response);
        }
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

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private <T> ResponseEntity<ApiResponse<T>> tokenResponse(ApiResponse<T> body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }
}

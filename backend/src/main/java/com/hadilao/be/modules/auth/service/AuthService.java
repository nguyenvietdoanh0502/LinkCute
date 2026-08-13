package com.hadilao.be.modules.auth.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.core.security.JwtProvider;
import com.hadilao.be.core.security.SessionRevocationService;
import com.hadilao.be.modules.auth.dto.*;
import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.dto.UserRegistrationCommand;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import com.hadilao.be.modules.user.service.UserRegistrationService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final DefaultRedisScript<Long> CONSUME_RESET_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private static final DefaultRedisScript<Long> ROTATE_REFRESH_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "redis.call('psetex', KEYS[1], ARGV[3], ARGV[2]); return 1 "
                            + "else return 0 end",
                    Long.class
            );

    private static final DefaultRedisScript<Long> REVOKE_REFRESH_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final UserRegistrationService userRegistrationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final OtpService otpService;
    private final MailService mailService;
    private final RateLimiterService rateLimiterService;
    private final SessionRevocationService sessionRevocationService;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${roamly.jwt.access-token-expiration}")
    private long jwtExpiration;

    @Value("${roamly.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${roamly.jwt.reset-password-expiration}")
    private long resetPasswordExpiration;

    public void register(RegisterRequest request, String ipAddress) {
        if (!rateLimiterService.isRegisterAllowed(request.getEmail(), ipAddress)) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        UserRegistrationCommand command = UserRegistrationCommand.builder()
                .email(request.getEmail())
                .hashedPassword(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .build();

        userRegistrationService.registerNewUser(command);

        String otpCode = otpService.generateAndSaveOtp(request.getEmail(), OtpService.OtpType.REGISTER);
        mailService.sendOtpEmail(request.getEmail(), otpCode);
    }

    public AuthenticationResult login(LoginRequest request, String ipAddress){
        if(!rateLimiterService.isLoginIpAllowed(ipAddress)){
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        String email = request.getEmail();
        if (rateLimiterService.isEmailLocked(email)) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
        if(existingUserOpt.isEmpty()){
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        User existingUser = existingUserOpt.get();

        if(existingUser.isDeleted()){
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        boolean isPasswordMatch = passwordEncoder.matches(request.getPassword(),existingUser.getPassword());
        if (!isPasswordMatch) {
            rateLimiterService.isLoginEmailAllowed(email);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        rateLimiterService.resetFailedLoginCount(email);

        if(existingUser.getStatus()== AccountStatus.PENDING){
            throw new AppException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }
        if(existingUser.getStatus()==AccountStatus.BANNED){
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }

        UserDTO userDTO = userRegistrationService.mapToDTO(existingUser);
        UserDetails userDetails =
                org.springframework.security.core.userdetails.User.builder()
                        .username(userDTO.getEmail())
                        .password("")
                        .authorities(Collections.emptyList())
                        .build();
        long sessionVersion = existingUser.getSessionVersion();
        String accessToken = jwtProvider.generateToken(userDetails, sessionVersion);
        String refreshToken = jwtProvider.generateRefreshToken(userDetails, sessionVersion);
        String redisKey = "refresh:" + userDTO.getEmail();
        storeRefreshToken(redisKey, refreshToken);
        AuthResponse response = AuthResponse.builder()
                .user(userDTO)
                .accessToken(accessToken)
                .expiresIn(jwtExpiration / 1000)
                .build();
        return new AuthenticationResult(response, refreshToken);
    }
    public TokenRefreshResult refreshToken(String refreshToken){
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RefreshTokenRejectedException(true);
        }

        UserDetails userDetails;
        Long sessionVersion;
        try {
            userDetails = jwtProvider.validateRefreshToken(refreshToken);
            sessionVersion = jwtProvider.extractSessionVersion(refreshToken);
        } catch (AppException | JwtException | IllegalArgumentException exception) {
            throw new RefreshTokenRejectedException(true);
        }
        if (sessionVersion == null) {
            throw new RefreshTokenRejectedException(true);
        }
        String email = userDetails.getUsername();
        String redisKey = "refresh:" + email;

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RefreshTokenRejectedException(true));
        if (user.isDeleted() || user.getStatus() == AccountStatus.BANNED) {
            sessionRevocationService.revokeAllSessions(user);
            throw new RefreshTokenRejectedException(true);
        }
        if (!sessionRevocationService.isSessionActive(user, sessionVersion)) {
            throw new RefreshTokenRejectedException(true);
        }

        String newAccessToken = jwtProvider.generateToken(userDetails, sessionVersion);
        String newRefreshToken = jwtProvider.generateRefreshToken(userDetails, sessionVersion);
        Long rotated = redisTemplate.execute(
                ROTATE_REFRESH_TOKEN_SCRIPT,
                List.of(redisKey),
                refreshToken,
                newRefreshToken,
                Long.toString(refreshTokenExpiration)
        );
        if (!Long.valueOf(1L).equals(rotated)) {
            // Do not clear the browser cookie here: another tab may have just won the rotation.
            throw new RefreshTokenRejectedException(false);
        }
        RefreshTokenResponse response = RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(jwtExpiration / 1000)
                .build();
        return new TokenRefreshResult(response, newRefreshToken);
    }

    public void logout(String refreshToken, String accessToken){
        revokeRefreshTokenIfCurrent(refreshToken);
        blacklistAccessTokenIfValid(accessToken);
    }

    private void revokeRefreshTokenIfCurrent(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            UserDetails userDetails = jwtProvider.validateRefreshToken(refreshToken);
            String redisKey = "refresh:" + userDetails.getUsername();
            redisTemplate.execute(
                    REVOKE_REFRESH_TOKEN_SCRIPT,
                    List.of(redisKey),
                    refreshToken
            );
        } catch (AppException | IllegalArgumentException ignored) {
            // Logout is idempotent. Invalid or expired client cookies are cleared by the controller.
        }
    }

    private void blacklistAccessTokenIfValid(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        String tokenId;
        try {
            if (!jwtProvider.isAccessToken(accessToken)) {
                return;
            }
            tokenId = jwtProvider.extractJwtId(accessToken);
        } catch (RuntimeException ignored) {
            // An expired or malformed access token must not prevent cookie cleanup.
            return;
        }
        if (tokenId != null && !tokenId.isBlank()) {
            redisTemplate.opsForValue().set(
                    "blacklist:jti:" + tokenId, "true",
                    Duration.ofMillis(jwtExpiration));
        }
    }

    @Transactional
    public void changePassword(ChangePasswordRequest changePasswordRequest){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(()->new AppException(ErrorCode.UNAUTHENTICATED));
        if(!passwordEncoder.matches(changePasswordRequest.getOldPassword(),user.getPassword())){
            throw new AppException(ErrorCode.INVALID_OLD_PASSWORD);
        }
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        sessionRevocationService.revokeAllSessions(user);
    }

    public void forgotPassword(ForgotPasswordRequest request, String ipAddress){
        if (!rateLimiterService.isForgotPasswordAllowed(request.getEmail(), ipAddress)) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
        if(existingUserOpt.isPresent()){
            User user = existingUserOpt.get();
            if(user.getStatus()!=AccountStatus.PENDING && user.getStatus()!= AccountStatus.BANNED){
                String otpCode = otpService.generateAndSaveOtp(request.getEmail(), OtpService.OtpType.FORGOT_PASSWORD);
                mailService.sendOtpEmailForResetPassword(request.getEmail(),otpCode);
            }
        }
    }

    public VerifyOtpForgotPasswordResponse verifyOtpForgotPassword(VerifyOtpRequest request, String ipAddress){
        if (!rateLimiterService.isOtpVerificationAllowed(
                request.getEmail(), ipAddress, OtpService.OtpType.FORGOT_PASSWORD)) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        boolean isValid = otpService.verifyOtp(request.getEmail(), request.getOtpCode(), OtpService.OtpType.FORGOT_PASSWORD);
        if (!isValid) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        UserDetails dummyUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username(request.getEmail())
                .password("")
                .authorities(Collections.emptyList())
                .build();

        String resetToken = jwtProvider.generateResetPasswordToken(dummyUserDetails);
        String redisKey = "reset_token:" + request.getEmail();

        redisTemplate.opsForValue().set(
                redisKey,
                resetToken,
                Duration.ofMillis(resetPasswordExpiration)
        );
        return VerifyOtpForgotPasswordResponse.builder().resetToken(resetToken).build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request){
        String redisKeyToken = "reset_token:" + request.getEmail();
        jwtProvider.validateResetPasswordToken(request.getResetToken(), request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.getStatus() == AccountStatus.BANNED) {
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }

        Long consumed = redisTemplate.execute(
                CONSUME_RESET_TOKEN_SCRIPT,
                List.of(redisKeyToken),
                request.getResetToken()
        );
        if (!Long.valueOf(1L).equals(consumed)) {
            throw new AppException(ErrorCode.INVALID_RESET_TOKEN);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sessionRevocationService.revokeAllSessions(user);
    }

    public AuthenticationResult verifyOtp(VerifyOtpRequest request, String ipAddress) {
        if (!rateLimiterService.isOtpVerificationAllowed(
                request.getEmail(), ipAddress, OtpService.OtpType.REGISTER)) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        boolean isValid = otpService.verifyOtp(request.getEmail(), request.getOtpCode(), OtpService.OtpType.REGISTER);
        if (!isValid) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        UserDTO userDTO = userRegistrationService.verifyUser(request.getEmail());

        UserDetails userDetails =
                org.springframework.security.core.userdetails.User.builder()
                        .username(userDTO.getEmail())
                        .password("")
                        .authorities(Collections.emptyList())
                        .build();

        User user = userRepository.findByEmail(userDTO.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        long sessionVersion = user.getSessionVersion();
        String accessToken = jwtProvider.generateToken(userDetails, sessionVersion);
        String refreshToken = jwtProvider.generateRefreshToken(userDetails, sessionVersion);
        storeRefreshToken("refresh:" + userDTO.getEmail(), refreshToken);

        AuthResponse response = AuthResponse.builder()
                .user(userDTO)
                .accessToken(accessToken)
                .expiresIn(jwtExpiration / 1000)
                .build();
        return new AuthenticationResult(response, refreshToken);
    }

    private void storeRefreshToken(String redisKey, String refreshToken) {
        redisTemplate.opsForValue().set(
                redisKey,
                refreshToken,
                Duration.ofMillis(refreshTokenExpiration)
        );
    }

    public record AuthenticationResult(AuthResponse response, String refreshToken) {
        @Override
        public String toString() {
            return "AuthenticationResult[REDACTED]";
        }
    }

    public record TokenRefreshResult(RefreshTokenResponse response, String refreshToken) {
        @Override
        public String toString() {
            return "TokenRefreshResult[REDACTED]";
        }
    }
}

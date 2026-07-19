package com.hadilao.be.core.security;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtProvider {
    private final UserDetailsService userDetailsService;

    @Value("${roamly.jwt.secret}")
    private String secretKey;

    @Value("${roamly.jwt.access-token-expiration}")
    private long jwtExpiration;

    @Value("${roamly.jwt.refresh-token-expiration}")
    private long refreshExpiration;

    @Value("${roamly.jwt.reset-password-expiration}")
    private long resetExpiration;

    public JwtProvider(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJwtId(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Long extractSessionVersion(String token) {
        Number sessionVersion = extractClaim(token, claims -> claims.get("sessionVersion", Number.class));
        return sessionVersion == null ? null : sessionVersion.longValue();
    }

    public boolean isAccessToken(String token) {
        String tokenType = extractClaim(token, claims -> claims.get("tokenType", String.class));
        return "access".equals(tokenType);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, 0L);
    }

    public String generateToken(UserDetails userDetails, long sessionVersion) {
        return buildToken(
                Map.of("tokenType", "access", "sessionVersion", sessionVersion),
                userDetails,
                jwtExpiration
        );
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> accessClaims = new HashMap<>(extraClaims);
        accessClaims.put("tokenType", "access");
        return buildToken(accessClaims, userDetails, jwtExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(userDetails, 0L);
    }

    public String generateRefreshToken(UserDetails userDetails, long sessionVersion) {
        return buildToken(
                Map.of("tokenType", "refresh", "sessionVersion", sessionVersion),
                userDetails,
                refreshExpiration
        );
    }

    public String generateResetPasswordToken(UserDetails userDetails) {
        return buildToken(Map.of("tokenType", "reset_password"), userDetails, resetExpiration);
    }

    public void validateResetPasswordToken(String resetToken, String expectedEmail) {
        try {
            Claims claims = extractAllClaims(resetToken);
            String tokenType = claims.get("tokenType", String.class);
            String subject = claims.getSubject();
            Date expiration = claims.getExpiration();

            boolean isValid = "reset_password".equals(tokenType)
                    && subject != null
                    && subject.equalsIgnoreCase(expectedEmail)
                    && expiration != null
                    && expiration.after(new Date());

            if (!isValid) {
                throw new AppException(ErrorCode.INVALID_RESET_TOKEN);
            }
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_RESET_TOKEN);
        }
    }

    public UserDetails validateRefreshToken(String refreshToken){
        String email = extractUsername(refreshToken);
        if(email==null){
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String tokenType = extractClaim(refreshToken,claims -> claims.get("tokenType",String.class));
        if(!"refresh".equals(tokenType)||isTokenExpired(refreshToken)){
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return userDetails;
    }
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && userDetails.isEnabled()
                && userDetails.isAccountNonLocked()
                && userDetails.isAccountNonExpired()
                && userDetails.isCredentialsNonExpired();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

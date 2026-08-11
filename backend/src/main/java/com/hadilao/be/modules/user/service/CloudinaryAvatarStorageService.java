package com.hadilao.be.modules.user.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.user.dto.StoredAvatar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CloudinaryAvatarStorageService implements AvatarStorageService {

    public static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public CloudinaryAvatarStorageService(
            Cloudinary cloudinary,
            @Value("${roamly.cloudinary.cloud-name:}") String cloudName,
            @Value("${roamly.cloudinary.api-key:}") String apiKey,
            @Value("${roamly.cloudinary.api-secret:}") String apiSecret,
            @Value("${roamly.cloudinary.folder:linkcute/avatars}") String folder) {
        this.cloudinary = cloudinary;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.folder = normalizeFolder(folder);
    }

    @Override
    public StoredAvatar upload(UUID userId, MultipartFile file) {
        requireConfigured();
        byte[] bytes = validateAndRead(file);
        String assetName = userId + "-" + UUID.randomUUID();
        String expectedPublicId = folder.isBlank() ? assetName : folder + "/" + assetName;

        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", "image");
        options.put("public_id", assetName);
        options.put("overwrite", false);
        if (!folder.isBlank()) {
            options.put("folder", folder);
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, options);
            String secureUrl = valueAsText(result.get("secure_url"));
            String publicId = valueAsText(result.get("public_id"));
            if (secureUrl == null || publicId == null) {
                cleanupFailedUpload(publicId == null ? expectedPublicId : publicId);
                throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED);
            }
            return new StoredAvatar(secureUrl, publicId);
        } catch (AppException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            cleanupFailedUpload(expectedPublicId);
            log.warn("Cloudinary avatar upload failed: {}", exception.getMessage());
            throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        requireConfigured();
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true));
        } catch (IOException | RuntimeException exception) {
            log.warn("Cloudinary avatar cleanup failed for public id {}: {}", publicId, exception.getMessage());
            throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED);
        }
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_AVATAR);
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new AppException(ErrorCode.AVATAR_TOO_LARGE);
        }

        String declaredType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(declaredType)) {
            throw new AppException(ErrorCode.INVALID_AVATAR);
        }

        try {
            byte[] bytes = file.getBytes();
            String detectedType = detectContentType(bytes);
            if (!declaredType.equals(detectedType)) {
                throw new AppException(ErrorCode.INVALID_AVATAR);
            }
            return bytes;
        } catch (AppException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INVALID_AVATAR);
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 12
                && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
            return "image/webp";
        }
        return null;
    }

    private void requireConfigured() {
        if (cloudName == null || cloudName.isBlank()
                || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new AppException(ErrorCode.AVATAR_STORAGE_NOT_CONFIGURED);
        }
    }

    private void cleanupFailedUpload(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true));
        } catch (IOException | RuntimeException cleanupException) {
            log.warn("Could not clean up an uncertain Cloudinary upload {}: {}",
                    publicId, cleanupException.getMessage());
        }
    }

    private static String normalizeFolder(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("^/+|/+$", "");
    }

    private static String valueAsText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}

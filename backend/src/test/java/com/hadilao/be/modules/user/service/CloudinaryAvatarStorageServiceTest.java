package com.hadilao.be.modules.user.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.user.dto.StoredAvatar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryAvatarStorageServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryAvatarStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new CloudinaryAvatarStorageService(
                cloudinary,
                "demo-cloud",
                "api-key",
                "api-secret",
                " /linkcute/avatars/ ");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void uploadValidImageUsesSafeServerGeneratedCloudinaryOptions() throws Exception {
        UUID userId = UUID.randomUUID();
        byte[] png = pngBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../unsafe-name.png", "image/png", png);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/avatar.png",
                "public_id", "linkcute/avatars/generated-id"));
        ArgumentCaptor<Map> optionsCaptor = ArgumentCaptor.forClass(Map.class);

        StoredAvatar result = storageService.upload(userId, file);

        assertThat(result.url()).isEqualTo("https://res.cloudinary.com/demo/avatar.png");
        assertThat(result.publicId()).isEqualTo("linkcute/avatars/generated-id");
        verify(uploader).upload(any(byte[].class), optionsCaptor.capture());
        Map<String, Object> options = optionsCaptor.getValue();
        assertThat(options)
                .containsEntry("resource_type", "image")
                .containsEntry("folder", "linkcute/avatars")
                .containsEntry("overwrite", false);
        assertThat(options.get("public_id").toString()).startsWith(userId + "-");
        assertThat(options.get("public_id").toString()).doesNotContain("unsafe-name");
    }

    @Test
    void uploadRejectsDeclaredTypeThatDoesNotMatchMagicBytes() {
        MockMultipartFile disguisedFile = new MockMultipartFile(
                "file", "avatar.png", "image/png",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThatThrownBy(() -> storageService.upload(UUID.randomUUID(), disguisedFile))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AVATAR);

        verifyNoInteractions(cloudinary);
    }

    @Test
    void uploadRejectsUnsupportedAndEmptyFiles() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "avatar.svg", "image/svg+xml", "<svg/>".getBytes());
        MockMultipartFile empty = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.upload(UUID.randomUUID(), svg))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AVATAR);
        assertThatThrownBy(() -> storageService.upload(UUID.randomUUID(), empty))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AVATAR);

        verifyNoInteractions(cloudinary);
    }

    @Test
    void uploadRejectsOversizedFileWithoutReadingIt() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(CloudinaryAvatarStorageService.MAX_AVATAR_BYTES + 1);

        assertThatThrownBy(() -> storageService.upload(UUID.randomUUID(), file))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AVATAR_TOO_LARGE);

        verify(file, never()).getBytes();
        verifyNoInteractions(cloudinary);
    }

    @Test
    void uploadFailsWithStableErrorWhenCloudinaryIsNotConfigured() {
        CloudinaryAvatarStorageService unconfigured = new CloudinaryAvatarStorageService(
                cloudinary, " ", "api-key", "api-secret", "avatars");

        assertThatThrownBy(() -> unconfigured.upload(UUID.randomUUID(), validPngFile()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AVATAR_STORAGE_NOT_CONFIGURED);

        verifyNoInteractions(cloudinary);
    }

    @Test
    void uploadMapsIoFailuresAndIncompleteResponsesToStableDomainError() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap()))
                .thenThrow(new IOException("network unavailable"))
                .thenReturn(Map.of("public_id", "avatars/id-without-url"));

        assertThatThrownBy(() -> storageService.upload(UUID.randomUUID(), validPngFile()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AVATAR_UPLOAD_FAILED);
        assertThatThrownBy(() -> storageService.upload(UUID.randomUUID(), validPngFile()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AVATAR_UPLOAD_FAILED);

        ArgumentCaptor<String> cleanupIds = ArgumentCaptor.forClass(String.class);
        verify(uploader, times(2)).destroy(cleanupIds.capture(), anyMap());
        assertThat(cleanupIds.getAllValues())
                .anyMatch(id -> id.startsWith("linkcute/avatars/"))
                .contains("avatars/id-without-url");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deleteInvalidatesTheStoredImageAndIgnoresBlankIds() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("linkcute/avatars/avatar-id"), anyMap()))
                .thenReturn(Map.of("result", "ok"));
        ArgumentCaptor<Map> optionsCaptor = ArgumentCaptor.forClass(Map.class);

        storageService.delete("linkcute/avatars/avatar-id");
        storageService.delete("  ");

        verify(uploader).destroy(eq("linkcute/avatars/avatar-id"), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue())
                .containsEntry("resource_type", "image")
                .containsEntry("invalidate", true);
    }

    private MockMultipartFile validPngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", pngBytes());
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }
}

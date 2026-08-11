package com.hadilao.be.modules.user.service;

import com.hadilao.be.modules.user.dto.StoredAvatar;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AvatarStorageService {

    StoredAvatar upload(UUID userId, MultipartFile file);

    void delete(String publicId);
}

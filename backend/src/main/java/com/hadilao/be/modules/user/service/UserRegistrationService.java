package com.hadilao.be.modules.user.service;

import com.hadilao.be.core.common.utils.PinCodeGenerator;
import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.dto.UserRegistrationCommand;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;

    @Transactional
    public UserDTO registerNewUser(UserRegistrationCommand command) {
        Optional<User> existingUserOpt = userRepository.findByEmail(command.getEmail());
        
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.getStatus() == AccountStatus.ACTIVE || existingUser.getStatus() == AccountStatus.BANNED) {
                throw new AppException(ErrorCode.USER_EXISTED);
            }
            // If PENDING, update password and full name, keep existing PIN
            existingUser.setPassword(command.getHashedPassword());
            existingUser.setFullName(command.getFullName());
            existingUser.setAvatarUrl("https://ui-avatars.com/api/?name=" + 
                    URLEncoder.encode(command.getFullName(), StandardCharsets.UTF_8) + "&background=random");
            User savedUser = userRepository.save(existingUser);
            return mapToDTO(savedUser);
        }

        // Generate unique PIN
        String pinCode = PinCodeGenerator.generate();
        int retries = 0;
        while (retries < 5 && userRepository.existsByPinCode(pinCode)) {
            pinCode = PinCodeGenerator.generate();
            retries++;
        }
        if (retries >= 5) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Could be a custom "SERVER_ERROR" for PIN collision
        }

        String defaultAvatar = "https://ui-avatars.com/api/?name=" + 
                URLEncoder.encode(command.getFullName(), StandardCharsets.UTF_8) + "&background=random";

        User user = User.builder()
                .email(command.getEmail())
                .password(command.getHashedPassword())
                .fullName(command.getFullName())
                .pinCode(pinCode)
                .status(AccountStatus.PENDING)
                .avatarUrl(defaultAvatar)
                .build();

        User savedUser = userRepository.save(user);
        return mapToDTO(savedUser);
    }

    public UserDTO mapToDTO(User savedUser) {
        return UserDTO.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .pinCode(savedUser.getPinCode())
                .avatarUrl(savedUser.getAvatarUrl())
                .build();
    }

    @Transactional
    public UserDTO verifyUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.getStatus() == AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        user.setStatus(AccountStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        return mapToDTO(savedUser);
    }
}

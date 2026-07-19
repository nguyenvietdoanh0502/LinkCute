package com.hadilao.be.modules.user.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.user.dto.UserDTO;
import com.hadilao.be.modules.user.dto.UserRegistrationCommand;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for UserRegistrationService")
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    @Nested
    @DisplayName("registerNewUser Tests")
    class RegisterNewUserTests {

        @Test
        @DisplayName("Should register a new user when email does not exist")
        void testRegisterNewUser_Success() {
            // Arrange
            UserRegistrationCommand command = UserRegistrationCommand.builder()
                    .email("new@example.com")
                    .hashedPassword("hashed_pwd")
                    .fullName("New User")
                    .build();

            when(userRepository.findByEmail(command.getEmail())).thenReturn(Optional.empty());
            when(userRepository.existsByPinCode(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(UUID.randomUUID());
                return user;
            });

            // Act
            UserDTO result = userRegistrationService.registerNewUser(command);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(command.getEmail());
            assertThat(result.getFullName()).isEqualTo(command.getFullName());
            assertThat(result.getPinCode()).startsWith("RML-");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw USER_EXISTED when email exists and status is ACTIVE")
        void testRegisterNewUser_UserExistsActive() {
            // Arrange
            UserRegistrationCommand command = UserRegistrationCommand.builder()
                    .email("active@example.com")
                    .build();

            User existingUser = User.builder()
                    .email(command.getEmail())
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(userRepository.findByEmail(command.getEmail())).thenReturn(Optional.of(existingUser));

            // Act & Assert
            assertThatThrownBy(() -> userRegistrationService.registerNewUser(command))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_EXISTED);
        }

        @Test
        @DisplayName("Should update user and return when email exists and status is PENDING")
        void testRegisterNewUser_UserExistsPending() {
            // Arrange
            UserRegistrationCommand command = UserRegistrationCommand.builder()
                    .email("pending@example.com")
                    .hashedPassword("new_hashed_pwd")
                    .fullName("Updated Name")
                    .build();

            User existingUser = User.builder()
                    .email(command.getEmail())
                    .status(AccountStatus.PENDING)
                    .pinCode("RML-111111")
                    .build();

            when(userRepository.findByEmail(command.getEmail())).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserDTO result = userRegistrationService.registerNewUser(command);

            // Assert
            assertThat(result.getFullName()).isEqualTo(command.getFullName());
            assertThat(result.getPinCode()).isEqualTo("RML-111111");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should handle PIN collision and retry")
        void testRegisterNewUser_PinCollisionRetry() {
            // Arrange
            UserRegistrationCommand command = UserRegistrationCommand.builder()
                    .email("collision@example.com")
                    .fullName("Collision Test")
                    .build();

            when(userRepository.findByEmail(command.getEmail())).thenReturn(Optional.empty());
            when(userRepository.existsByPinCode(anyString()))
                    .thenReturn(true) // First try: collision
                    .thenReturn(false); // Second try: success

            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserDTO result = userRegistrationService.registerNewUser(command);

            // Assert
            assertThat(result).isNotNull();
            verify(userRepository, times(2)).existsByPinCode(anyString());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when PIN collision retries exceed limit")
        void testRegisterNewUser_PinCollisionExceedLimit() {
            // Arrange
            UserRegistrationCommand command = UserRegistrationCommand.builder()
                    .email("limit@example.com")
                    .fullName("Limit Test")
                    .build();

            when(userRepository.findByEmail(command.getEmail())).thenReturn(Optional.empty());
            when(userRepository.existsByPinCode(anyString())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userRegistrationService.registerNewUser(command))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNCATEGORIZED_EXCEPTION);
            
            verify(userRepository, times(5)).existsByPinCode(anyString());
        }
    }

    @Nested
    @DisplayName("verifyUser Tests")
    class VerifyUserTests {

        @Test
        @DisplayName("Should verify user successfully when status is PENDING")
        void testVerifyUser_Success() {
            // Arrange
            String email = "verify@example.com";
            User user = User.builder()
                    .email(email)
                    .status(AccountStatus.PENDING)
                    .build();

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserDTO result = userRegistrationService.verifyUser(email);

            // Assert
            assertThat(result.getEmail()).isEqualTo(email);
            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Should throw USER_NOT_EXISTED when email not found")
        void testVerifyUser_NotFound() {
            // Arrange
            String email = "missing@example.com";
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userRegistrationService.verifyUser(email))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_EXISTED);
        }

        @Test
        @DisplayName("Should throw USER_EXISTED when user is already ACTIVE")
        void testVerifyUser_AlreadyActive() {
            // Arrange
            String email = "active@example.com";
            User user = User.builder()
                    .email(email)
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // Act & Assert
            assertThatThrownBy(() -> userRegistrationService.verifyUser(email))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_EXISTED);
        }
    }
}

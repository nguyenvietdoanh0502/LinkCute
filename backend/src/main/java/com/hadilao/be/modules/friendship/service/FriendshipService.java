package com.hadilao.be.modules.friendship.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.friendship.dto.FriendDTO;
import com.hadilao.be.modules.friendship.dto.FriendRequestDTO;
import com.hadilao.be.modules.friendship.dto.FriendSearchDTO;
import com.hadilao.be.modules.friendship.dto.FriendUserDTO;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.friendship.enums.RelationshipStatus;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private static final Pattern FRIEND_PIN_PATTERN = Pattern.compile("^RML-\\d{6}$");

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional(readOnly = true)
    public FriendSearchDTO searchByPinCode(String pinCode) {
        User currentUser = getCurrentUser();
        String normalizedPin = normalizePinCode(pinCode);
        User target = userRepository
                .findByPinCodeAndIsDeletedFalseAndStatus(normalizedPin, AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (currentUser.getId().equals(target.getId())) {
            throw new AppException(ErrorCode.CANNOT_FRIEND_SELF);
        }

        UserPair pair = canonicalPair(currentUser, target);
        Friendship friendship = friendshipRepository
                .findByUserPair(pair.low().getId(), pair.high().getId())
                .orElse(null);

        return FriendSearchDTO.builder()
                .id(target.getId())
                .fullName(target.getFullName())
                .avatarUrl(target.getAvatarUrl())
                .pinCode(target.getPinCode())
                .relationshipStatus(relationshipStatus(friendship, currentUser.getId()))
                .friendshipId(friendship == null ? null : friendship.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public List<FriendDTO> getFriends() {
        User currentUser = getCurrentUser();
        return friendshipRepository
                .findAllForUserByStatus(currentUser.getId(), FriendshipStatus.ACCEPTED)
                .stream()
                .filter(friendship -> isAvailable(otherUser(friendship, currentUser.getId())))
                .map(friendship -> toFriendDTO(friendship, currentUser.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestDTO> getIncomingRequests() {
        User currentUser = getCurrentUser();
        return friendshipRepository.findIncomingRequests(currentUser.getId()).stream()
                .filter(friendship -> isAvailable(otherUser(friendship, currentUser.getId())))
                .map(friendship -> toFriendRequestDTO(friendship, currentUser.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestDTO> getOutgoingRequests() {
        User currentUser = getCurrentUser();
        return friendshipRepository.findOutgoingRequests(currentUser.getId()).stream()
                .filter(friendship -> isAvailable(otherUser(friendship, currentUser.getId())))
                .map(friendship -> toFriendRequestDTO(friendship, currentUser.getId()))
                .toList();
    }

    @Transactional
    public FriendRequestDTO sendRequest(UUID addresseeId) {
        User authenticatedUser = getCurrentUser();
        if (authenticatedUser.getId().equals(addresseeId)) {
            throw new AppException(ErrorCode.CANNOT_FRIEND_SELF);
        }

        User addressee = userRepository
                .findByIdAndIsDeletedFalseAndStatus(addresseeId, AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        UserPair initialPair = canonicalPair(authenticatedUser, addressee);

        // Lock both users in one deterministic order. Reverse requests therefore
        // serialize on the same rows instead of both inserting a relationship.
        User lockedLow = lockUser(initialPair.low().getId());
        User lockedHigh = lockUser(initialPair.high().getId());
        User lockedRequester = authenticatedUser.getId().equals(lockedLow.getId())
                ? lockedLow
                : lockedHigh;
        User lockedAddressee = addresseeId.equals(lockedLow.getId())
                ? lockedLow
                : lockedHigh;

        if (!isAvailable(lockedRequester)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (!isAvailable(lockedAddressee)) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }

        Friendship existing = friendshipRepository
                .findByUserPair(lockedLow.getId(), lockedHigh.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new AppException(ErrorCode.ALREADY_FRIENDS);
            }
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
        }

        Friendship friendship = Friendship.builder()
                .userLow(lockedLow)
                .userHigh(lockedHigh)
                .requester(lockedRequester)
                .status(FriendshipStatus.PENDING)
                .build();

        try {
            Friendship saved = friendshipRepository.saveAndFlush(friendship);
            return toFriendRequestDTO(saved, lockedRequester.getId());
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
        }
    }

    @Transactional
    public FriendDTO acceptRequest(UUID requestId) {
        User currentUser = getCurrentUser();
        Friendship friendship = friendshipRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (friendship.getStatus() != FriendshipStatus.PENDING
                || !involves(friendship, currentUser.getId())
                || friendship.getRequester().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        User requester = friendship.getRequester();
        if (!isAvailable(requester)) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        friendship.accept();
        Friendship saved = friendshipRepository.saveAndFlush(friendship);
        return toFriendDTO(saved, currentUser.getId());
    }

    @Transactional
    public void deleteRequest(UUID requestId) {
        User currentUser = getCurrentUser();
        Friendship friendship = friendshipRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (friendship.getStatus() != FriendshipStatus.PENDING
                || !involves(friendship, currentUser.getId())) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void removeFriend(UUID friendshipId) {
        User currentUser = getCurrentUser();
        Friendship friendship = friendshipRepository.findByIdForUpdate(friendshipId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED
                || !involves(friendship, currentUser.getId())) {
            throw new AppException(ErrorCode.FRIENDSHIP_NOT_FOUND);
        }

        friendshipRepository.delete(friendship);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userRepository.findByEmail(authentication.getName())
                .filter(this::isAvailable)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private User lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private String normalizePinCode(String pinCode) {
        if (pinCode == null) {
            throw new AppException(ErrorCode.INVALID_PIN_CODE);
        }
        String normalized = pinCode.trim().toUpperCase(Locale.ROOT);
        if (!FRIEND_PIN_PATTERN.matcher(normalized).matches()) {
            throw new AppException(ErrorCode.INVALID_PIN_CODE);
        }
        return normalized;
    }

    private UserPair canonicalPair(User first, User second) {
        if (first.getId().toString().compareTo(second.getId().toString()) < 0) {
            return new UserPair(first, second);
        }
        return new UserPair(second, first);
    }

    private RelationshipStatus relationshipStatus(Friendship friendship, UUID currentUserId) {
        if (friendship == null) {
            return RelationshipStatus.NONE;
        }
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return RelationshipStatus.FRIENDS;
        }
        return friendship.getRequester().getId().equals(currentUserId)
                ? RelationshipStatus.OUTGOING_PENDING
                : RelationshipStatus.INCOMING_PENDING;
    }

    private boolean involves(Friendship friendship, UUID userId) {
        return friendship.getUserLow().getId().equals(userId)
                || friendship.getUserHigh().getId().equals(userId);
    }

    private User otherUser(Friendship friendship, UUID currentUserId) {
        if (friendship.getUserLow().getId().equals(currentUserId)) {
            return friendship.getUserHigh();
        }
        if (friendship.getUserHigh().getId().equals(currentUserId)) {
            return friendship.getUserLow();
        }
        throw new AppException(ErrorCode.FRIENDSHIP_NOT_FOUND);
    }

    private boolean isAvailable(User user) {
        return user != null && !user.isDeleted() && user.getStatus() == AccountStatus.ACTIVE;
    }

    private FriendDTO toFriendDTO(Friendship friendship, UUID currentUserId) {
        return FriendDTO.builder()
                .friendshipId(friendship.getId())
                .user(toFriendUserDTO(otherUser(friendship, currentUserId)))
                .friendsSince(friendship.getAcceptedAt())
                .build();
    }

    private FriendRequestDTO toFriendRequestDTO(Friendship friendship, UUID currentUserId) {
        return FriendRequestDTO.builder()
                .id(friendship.getId())
                .user(toFriendUserDTO(otherUser(friendship, currentUserId)))
                .createdAt(friendship.getCreatedAt())
                .build();
    }

    private FriendUserDTO toFriendUserDTO(User user) {
        return FriendUserDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .pinCode(user.getPinCode())
                .build();
    }

    private record UserPair(User low, User high) {
    }
}

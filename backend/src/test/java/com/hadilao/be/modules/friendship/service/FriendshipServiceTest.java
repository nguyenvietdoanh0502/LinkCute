package com.hadilao.be.modules.friendship.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.friendship.dto.FriendDTO;
import com.hadilao.be.modules.friendship.dto.FriendSearchDTO;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.friendship.enums.RelationshipStatus;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    private static final UUID CURRENT_ID = UUID.fromString("80000000-0000-0000-0000-000000000000");
    private static final UUID TARGET_ID = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private FriendshipService friendshipService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendRequestUsesUuidStringOrderForTheCanonicalPair() {
        User current = activeUser(CURRENT_ID, "current@example.com", "Current", "RML-100001");
        User target = activeUser(TARGET_ID, "target@example.com", "Target", "RML-100002");
        authenticate(current);
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(TARGET_ID, AccountStatus.ACTIVE))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findByIdForUpdate(CURRENT_ID)).thenReturn(Optional.of(current));
        when(friendshipRepository.findByUserPair(TARGET_ID, CURRENT_ID)).thenReturn(Optional.empty());
        UUID requestId = UUID.randomUUID();
        when(friendshipRepository.saveAndFlush(any(Friendship.class))).thenAnswer(invocation -> {
            Friendship friendship = invocation.getArgument(0);
            friendship.setId(requestId);
            friendship.setCreatedAt(Instant.parse("2026-08-05T08:00:00Z"));
            return friendship;
        });

        var result = friendshipService.sendRequest(TARGET_ID);

        ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).saveAndFlush(captor.capture());
        Friendship saved = captor.getValue();
        assertThat(saved.getUserLow()).isSameAs(target);
        assertThat(saved.getUserHigh()).isSameAs(current);
        assertThat(saved.getRequester()).isSameAs(current);
        assertThat(saved.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.getId()).isEqualTo(requestId);
        assertThat(result.getUser().getId()).isEqualTo(TARGET_ID);
    }

    @Test
    void sendRequestRejectsSelfBeforeLookingUpTheAddressee() {
        User current = activeUser(UUID.randomUUID(), "current@example.com", "Current", "RML-100003");
        authenticate(current);

        assertError(() -> friendshipService.sendRequest(current.getId()), ErrorCode.CANNOT_FRIEND_SELF);

        verify(userRepository, never())
                .findByIdAndIsDeletedFalseAndStatus(any(UUID.class), any(AccountStatus.class));
        verifyNoInteractions(friendshipRepository);
    }

    @ParameterizedTest
    @MethodSource("existingRelationshipCases")
    void sendRequestRejectsAnExistingRelationship(
            FriendshipStatus existingStatus,
            ErrorCode expectedError
    ) {
        User current = activeUser(UUID.randomUUID(), "current@example.com", "Current", "RML-110001");
        User target = activeUser(UUID.randomUUID(), "target@example.com", "Target", "RML-110002");
        authenticate(current);
        stubLockedPair(current, target);
        Friendship existing = friendship(UUID.randomUUID(), current, target, current, existingStatus);
        User low = lower(current, target);
        User high = other(low, current, target);
        when(friendshipRepository.findByUserPair(low.getId(), high.getId()))
                .thenReturn(Optional.of(existing));

        assertError(() -> friendshipService.sendRequest(target.getId()), expectedError);

        verify(friendshipRepository, never()).saveAndFlush(any(Friendship.class));
    }

    @Test
    void sendRequestTranslatesTheUniqueConstraintRace() {
        User current = activeUser(UUID.randomUUID(), "current@example.com", "Current", "RML-120001");
        User target = activeUser(UUID.randomUUID(), "target@example.com", "Target", "RML-120002");
        authenticate(current);
        stubLockedPair(current, target);
        User low = lower(current, target);
        User high = other(low, current, target);
        when(friendshipRepository.findByUserPair(low.getId(), high.getId())).thenReturn(Optional.empty());
        when(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate pair"));

        assertError(
                () -> friendshipService.sendRequest(target.getId()),
                ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS
        );
    }

    @Test
    void recipientCanAcceptAPendingRequest() {
        User requester = activeUser(UUID.randomUUID(), "sender@example.com", "Sender", "RML-200001");
        User recipient = activeUser(UUID.randomUUID(), "recipient@example.com", "Recipient", "RML-200002");
        authenticate(recipient);
        UUID requestId = UUID.randomUUID();
        Friendship pending = friendship(
                requestId,
                requester,
                recipient,
                requester,
                FriendshipStatus.PENDING
        );
        when(friendshipRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pending));
        when(friendshipRepository.saveAndFlush(pending)).thenReturn(pending);

        FriendDTO result = friendshipService.acceptRequest(requestId);

        assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(pending.getAcceptedAt()).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo(requester.getId());
        assertThat(result.getFriendsSince()).isEqualTo(pending.getAcceptedAt());
        verify(friendshipRepository).saveAndFlush(pending);
    }

    @Test
    void requesterCannotAcceptTheirOwnRequest() {
        User requester = activeUser(UUID.randomUUID(), "sender@example.com", "Sender", "RML-210001");
        User recipient = activeUser(UUID.randomUUID(), "recipient@example.com", "Recipient", "RML-210002");
        authenticate(requester);
        UUID requestId = UUID.randomUUID();
        Friendship pending = friendship(
                requestId,
                requester,
                recipient,
                requester,
                FriendshipStatus.PENDING
        );
        when(friendshipRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pending));

        assertError(() -> friendshipService.acceptRequest(requestId), ErrorCode.FRIEND_REQUEST_NOT_FOUND);

        verify(friendshipRepository, never()).saveAndFlush(any(Friendship.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void eitherParticipantCanDeleteAPendingRequest(boolean actAsRequester) {
        User requester = activeUser(UUID.randomUUID(), "sender@example.com", "Sender", "RML-220001");
        User recipient = activeUser(UUID.randomUUID(), "recipient@example.com", "Recipient", "RML-220002");
        authenticate(actAsRequester ? requester : recipient);
        UUID requestId = UUID.randomUUID();
        Friendship pending = friendship(
                requestId,
                requester,
                recipient,
                requester,
                FriendshipStatus.PENDING
        );
        when(friendshipRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pending));

        friendshipService.deleteRequest(requestId);

        verify(friendshipRepository).delete(pending);
    }

    @Test
    void outsiderCannotDeleteARequest() {
        User requester = activeUser(UUID.randomUUID(), "sender@example.com", "Sender", "RML-230001");
        User recipient = activeUser(UUID.randomUUID(), "recipient@example.com", "Recipient", "RML-230002");
        User outsider = activeUser(UUID.randomUUID(), "outsider@example.com", "Outsider", "RML-230003");
        authenticate(outsider);
        UUID requestId = UUID.randomUUID();
        Friendship pending = friendship(
                requestId,
                requester,
                recipient,
                requester,
                FriendshipStatus.PENDING
        );
        when(friendshipRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pending));

        assertError(() -> friendshipService.deleteRequest(requestId), ErrorCode.FRIEND_REQUEST_NOT_FOUND);

        verify(friendshipRepository, never()).delete(any(Friendship.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void eitherFriendCanRemoveAnAcceptedFriendship(boolean actAsRequester) {
        User requester = activeUser(UUID.randomUUID(), "sender@example.com", "Sender", "RML-240001");
        User recipient = activeUser(UUID.randomUUID(), "recipient@example.com", "Recipient", "RML-240002");
        authenticate(actAsRequester ? requester : recipient);
        UUID friendshipId = UUID.randomUUID();
        Friendship accepted = friendship(
                friendshipId,
                requester,
                recipient,
                requester,
                FriendshipStatus.ACCEPTED
        );
        when(friendshipRepository.findByIdForUpdate(friendshipId)).thenReturn(Optional.of(accepted));

        friendshipService.removeFriend(friendshipId);

        verify(friendshipRepository).delete(accepted);
    }

    @Test
    void aPendingRequestCannotBeRemovedAsAFriendship() {
        User requester = activeUser(UUID.randomUUID(), "sender@example.com", "Sender", "RML-250001");
        User recipient = activeUser(UUID.randomUUID(), "recipient@example.com", "Recipient", "RML-250002");
        authenticate(recipient);
        UUID friendshipId = UUID.randomUUID();
        Friendship pending = friendship(
                friendshipId,
                requester,
                recipient,
                requester,
                FriendshipStatus.PENDING
        );
        when(friendshipRepository.findByIdForUpdate(friendshipId)).thenReturn(Optional.of(pending));

        assertError(() -> friendshipService.removeFriend(friendshipId), ErrorCode.FRIENDSHIP_NOT_FOUND);

        verify(friendshipRepository, never()).delete(any(Friendship.class));
    }

    @ParameterizedTest
    @MethodSource("relationshipStatusCases")
    void searchNormalizesPinAndReportsRelationshipStatus(
            FriendshipStatus storedStatus,
            boolean requesterIsCurrent,
            RelationshipStatus expectedStatus
    ) {
        User current = activeUser(UUID.randomUUID(), "current@example.com", "Current", "RML-300001");
        User target = activeUser(UUID.randomUUID(), "target@example.com", "Target", "RML-123456");
        authenticate(current);
        when(userRepository.findByPinCodeAndIsDeletedFalseAndStatus("RML-123456", AccountStatus.ACTIVE))
                .thenReturn(Optional.of(target));
        User low = lower(current, target);
        User high = other(low, current, target);
        Friendship stored = storedStatus == null
                ? null
                : friendship(
                        UUID.randomUUID(),
                        current,
                        target,
                        requesterIsCurrent ? current : target,
                        storedStatus
                );
        when(friendshipRepository.findByUserPair(low.getId(), high.getId()))
                .thenReturn(Optional.ofNullable(stored));

        FriendSearchDTO result = friendshipService.searchByPinCode("  rml-123456 ");

        assertThat(result.getId()).isEqualTo(target.getId());
        assertThat(result.getRelationshipStatus()).isEqualTo(expectedStatus);
        assertThat(result.getFriendshipId()).isEqualTo(stored == null ? null : stored.getId());
    }

    @Test
    void searchRejectsMalformedPinCode() {
        User current = activeUser(UUID.randomUUID(), "current@example.com", "Current", "RML-310001");
        authenticate(current);

        assertError(() -> friendshipService.searchByPinCode("not-a-pin"), ErrorCode.INVALID_PIN_CODE);

        verify(userRepository, never())
                .findByPinCodeAndIsDeletedFalseAndStatus(any(String.class), any(AccountStatus.class));
    }

    @Test
    void getFriendsMapsTheOtherEndpointAndFiltersUnavailableUsers() {
        User current = activeUser(UUID.randomUUID(), "current@example.com", "Current", "RML-400001");
        User activeFriend = activeUser(UUID.randomUUID(), "active@example.com", "Active", "RML-400002");
        User bannedFriend = activeUser(UUID.randomUUID(), "banned@example.com", "Banned", "RML-400003");
        bannedFriend.setStatus(AccountStatus.BANNED);
        User deletedFriend = activeUser(UUID.randomUUID(), "deleted@example.com", "Deleted", "RML-400004");
        deletedFriend.setDeleted(true);
        authenticate(current);
        Friendship visible = friendship(
                UUID.randomUUID(), current, activeFriend, current, FriendshipStatus.ACCEPTED);
        Friendship banned = friendship(
                UUID.randomUUID(), current, bannedFriend, current, FriendshipStatus.ACCEPTED);
        Friendship deleted = friendship(
                UUID.randomUUID(), current, deletedFriend, current, FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findAllForUserByStatus(current.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(visible, banned, deleted));

        List<FriendDTO> result = friendshipService.getFriends();

        assertThat(result).singleElement().satisfies(friend -> {
            assertThat(friend.getFriendshipId()).isEqualTo(visible.getId());
            assertThat(friend.getUser().getId()).isEqualTo(activeFriend.getId());
            assertThat(friend.getFriendsSince()).isEqualTo(visible.getAcceptedAt());
        });
    }

    @Test
    void unauthenticatedCallsAreRejected() {
        SecurityContextHolder.clearContext();

        assertError(friendshipService::getFriends, ErrorCode.UNAUTHENTICATED);

        verifyNoInteractions(friendshipRepository);
    }

    private static Stream<Arguments> existingRelationshipCases() {
        return Stream.of(
                Arguments.of(FriendshipStatus.PENDING, ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS),
                Arguments.of(FriendshipStatus.ACCEPTED, ErrorCode.ALREADY_FRIENDS)
        );
    }

    private static Stream<Arguments> relationshipStatusCases() {
        return Stream.of(
                Arguments.of(null, false, RelationshipStatus.NONE),
                Arguments.of(FriendshipStatus.PENDING, true, RelationshipStatus.OUTGOING_PENDING),
                Arguments.of(FriendshipStatus.PENDING, false, RelationshipStatus.INCOMING_PENDING),
                Arguments.of(FriendshipStatus.ACCEPTED, true, RelationshipStatus.FRIENDS)
        );
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of())
        );
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private void stubLockedPair(User current, User target) {
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(target.getId(), AccountStatus.ACTIVE))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdForUpdate(current.getId())).thenReturn(Optional.of(current));
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
    }

    private User activeUser(UUID id, String email, String fullName, String pinCode) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .pinCode(pinCode)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private Friendship friendship(
            UUID id,
            User first,
            User second,
            User requester,
            FriendshipStatus status
    ) {
        User low = lower(first, second);
        User high = other(low, first, second);
        Friendship friendship = Friendship.builder()
                .id(id)
                .userLow(low)
                .userHigh(high)
                .requester(requester)
                .status(status)
                .createdAt(Instant.parse("2026-08-05T07:00:00Z"))
                .updatedAt(Instant.parse("2026-08-05T07:00:00Z"))
                .build();
        if (status == FriendshipStatus.ACCEPTED) {
            friendship.accept();
        }
        return friendship;
    }

    private User lower(User first, User second) {
        return first.getId().toString().compareTo(second.getId().toString()) < 0 ? first : second;
    }

    private User other(User selected, User first, User second) {
        return selected == first ? second : first;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, ErrorCode errorCode) {
        assertThatThrownBy(operation)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }
}

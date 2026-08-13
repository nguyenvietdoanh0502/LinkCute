package com.hadilao.be.modules.call.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.RateLimiterService;
import com.hadilao.be.modules.call.dto.CallSignalDelivery;
import com.hadilao.be.modules.call.dto.CallSignalRequest;
import com.hadilao.be.modules.call.enums.CallSignalType;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallSignalingServiceTest {

    private static final UUID RECIPIENT_ID = id(1);
    private static final UUID SENDER_ID = id(2);
    private static final UUID CALL_ID = id(10);
    private static final String SENDER_EMAIL = "sender@example.com";
    private static final String RECIPIENT_EMAIL = "recipient@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private RateLimiterService rateLimiterService;

    @InjectMocks
    private CallSignalingService callSignalingService;

    @Test
    void acceptedFriendsCanRelayAnOfferWithServerDerivedSenderIdentity() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.ACCEPTED)));
        stubSignalAllowed();
        stubOfferAllowed();
        CallSignalRequest request = CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.OFFER)
                .sdp("v=0\r\no=caller")
                .build();

        CallSignalDelivery delivery = callSignalingService.relaySignal(SENDER_EMAIL, request);

        assertThat(delivery.recipientUsername()).isEqualTo(RECIPIENT_EMAIL);
        assertThat(delivery.signal()).satisfies(signal -> {
            assertThat(signal.getCallId()).isEqualTo(CALL_ID);
            assertThat(signal.getSenderUserId()).isEqualTo(SENDER_ID);
            assertThat(signal.getRecipientUserId()).isEqualTo(RECIPIENT_ID);
            assertThat(signal.getType()).isEqualTo(CallSignalType.OFFER);
            assertThat(signal.getSdp()).isEqualTo("v=0\r\no=caller");
            assertThat(signal.getCandidate()).isNull();
            assertThat(signal.getSdpMid()).isNull();
            assertThat(signal.getSdpMLineIndex()).isNull();
        });
        verify(rateLimiterService).isAllowed(
                "rate_limit:call:signal:sender:" + SENDER_ID,
                300,
                Duration.ofMinutes(1)
        );
        verify(rateLimiterService).isAllowed(
                "rate_limit:call:signal:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                200,
                Duration.ofMinutes(1)
        );
        verify(rateLimiterService).isAllowed(
                "rate_limit:call:offer:sender:" + SENDER_ID,
                10,
                Duration.ofMinutes(1)
        );
        verify(rateLimiterService).isAllowed(
                "rate_limit:call:offer:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                4,
                Duration.ofMinutes(1)
        );
    }

    @Test
    void offerIsRejectedWhenTheSenderGlobalLimitIsExceeded() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.ACCEPTED)));
        stubSignalAllowed();
        when(rateLimiterService.isAllowed(
                "rate_limit:call:offer:sender:" + SENDER_ID,
                10,
                Duration.ofMinutes(1)
        )).thenReturn(false);
        when(rateLimiterService.isAllowed(
                "rate_limit:call:offer:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                4,
                Duration.ofMinutes(1)
        )).thenReturn(true);

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, offerRequest()),
                ErrorCode.RATE_LIMIT_EXCEEDED
        );

        verify(rateLimiterService).isAllowed(
                "rate_limit:call:offer:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                4,
                Duration.ofMinutes(1)
        );
    }

    @Test
    void offerIsRejectedWhenTheSenderRecipientPairLimitIsExceeded() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.ACCEPTED)));
        stubSignalAllowed();
        when(rateLimiterService.isAllowed(
                "rate_limit:call:offer:sender:" + SENDER_ID,
                10,
                Duration.ofMinutes(1)
        )).thenReturn(true);
        when(rateLimiterService.isAllowed(
                "rate_limit:call:offer:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                4,
                Duration.ofMinutes(1)
        )).thenReturn(false);

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, offerRequest()),
                ErrorCode.RATE_LIMIT_EXCEEDED
        );
    }

    @Test
    void iceCandidateFieldsAreRelayedWithoutTrustingClientSenderData() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.ACCEPTED)));
        stubSignalAllowed();
        CallSignalRequest request = CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.ICE_CANDIDATE)
                .candidate("candidate:1 1 UDP 2122260223 192.0.2.1 54400 typ host")
                .sdpMid("audio")
                .sdpMLineIndex(0)
                .build();

        CallSignalDelivery delivery = callSignalingService.relaySignal(SENDER_EMAIL, request);

        assertThat(delivery.signal()).satisfies(signal -> {
            assertThat(signal.getSenderUserId()).isEqualTo(SENDER_ID);
            assertThat(signal.getType()).isEqualTo(CallSignalType.ICE_CANDIDATE);
            assertThat(signal.getCandidate()).isEqualTo(request.getCandidate());
            assertThat(signal.getSdpMid()).isEqualTo("audio");
            assertThat(signal.getSdpMLineIndex()).isZero();
        });
        verify(rateLimiterService).isAllowed(
                "rate_limit:call:signal:sender:" + SENDER_ID,
                300,
                Duration.ofMinutes(1)
        );
        verify(rateLimiterService).isAllowed(
                "rate_limit:call:signal:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                200,
                Duration.ofMinutes(1)
        );
        verify(rateLimiterService, never()).isAllowed(
                "rate_limit:call:offer:sender:" + SENDER_ID,
                10,
                Duration.ofMinutes(1)
        );
        verify(rateLimiterService, never()).isAllowed(
                "rate_limit:call:offer:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                4,
                Duration.ofMinutes(1)
        );
    }

    @Test
    void iceCandidateMetadataMayBeOmitted() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.ACCEPTED)));
        stubSignalAllowed();
        CallSignalRequest request = iceRequest("candidate:1", null, null);

        CallSignalDelivery delivery = callSignalingService.relaySignal(SENDER_EMAIL, request);

        assertThat(delivery.signal().getCandidate()).isEqualTo("candidate:1");
        assertThat(delivery.signal().getSdpMid()).isNull();
        assertThat(delivery.signal().getSdpMLineIndex()).isNull();
    }

    @Test
    void nonOfferSignalIsRejectedWhenTheSenderGlobalFrameLimitIsExceeded() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.ACCEPTED)));
        when(rateLimiterService.isAllowed(
                "rate_limit:call:signal:sender:" + SENDER_ID,
                300,
                Duration.ofMinutes(1)
        )).thenReturn(false);
        when(rateLimiterService.isAllowed(
                "rate_limit:call:signal:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                200,
                Duration.ofMinutes(1)
        )).thenReturn(true);

        assertError(
                () -> callSignalingService.relaySignal(
                        SENDER_EMAIL,
                        request(RECIPIENT_ID)
                ),
                ErrorCode.RATE_LIMIT_EXCEEDED
        );

        verify(rateLimiterService).isAllowed(
                "rate_limit:call:signal:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                200,
                Duration.ofMinutes(1)
        );
    }

    @Test
    void nonOfferSignalIsRejectedWhenTheSenderRecipientFrameLimitIsExceeded() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.ACCEPTED)));
        when(rateLimiterService.isAllowed(
                "rate_limit:call:signal:sender:" + SENDER_ID,
                300,
                Duration.ofMinutes(1)
        )).thenReturn(true);
        when(rateLimiterService.isAllowed(
                "rate_limit:call:signal:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                200,
                Duration.ofMinutes(1)
        )).thenReturn(false);

        assertError(
                () -> callSignalingService.relaySignal(
                        SENDER_EMAIL,
                        request(RECIPIENT_ID)
                ),
                ErrorCode.RATE_LIMIT_EXCEEDED
        );

        verify(rateLimiterService, never()).isAllowed(
                "rate_limit:call:offer:sender:" + SENDER_ID,
                10,
                Duration.ofMinutes(1)
        );
    }

    @Test
    void invalidEnvelopeIsRejectedBeforeLookingUpTheRecipient() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.of(sender));
        CallSignalRequest request = CallSignalRequest.builder()
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.HANGUP)
                .build();

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, request),
                ErrorCode.INVALID_CALL_SIGNAL
        );

        verify(userRepository, never()).findByIdAndIsDeletedFalseAndStatus(
                RECIPIENT_ID,
                AccountStatus.ACTIVE
        );
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void offerAndAnswerRequireBoundedSdpAndForbidIceFields() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.of(sender));

        assertInvalidSignal(CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.OFFER)
                .sdp("   ")
                .build());
        assertInvalidSignal(CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.ANSWER)
                .sdp("s".repeat(12 * 1024 + 1))
                .build());
        assertInvalidSignal(CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.OFFER)
                .sdp("v=0")
                .candidate("candidate:1")
                .build());

        verify(userRepository, never()).findByIdAndIsDeletedFalseAndStatus(
                RECIPIENT_ID,
                AccountStatus.ACTIVE
        );
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void iceCandidateRequiresBoundedCandidateAndSaneOptionalMetadata() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.of(sender));

        assertInvalidSignal(iceRequest(null, "audio", 0));
        assertInvalidSignal(iceRequest("c".repeat(4 * 1024 + 1), "audio", 0));
        assertInvalidSignal(iceRequest("candidate:1", " ", 0));
        assertInvalidSignal(iceRequest("candidate:1", "m".repeat(257), 0));
        assertInvalidSignal(iceRequest("candidate:1", "audio", -1));
        assertInvalidSignal(iceRequest("candidate:1", "audio", 65_536));

        CallSignalRequest withSdp = iceRequest("candidate:1", "audio", 0);
        withSdp.setSdp("v=0");
        assertInvalidSignal(withSdp);

        verify(userRepository, never()).findByIdAndIsDeletedFalseAndStatus(
                RECIPIENT_ID,
                AccountStatus.ACTIVE
        );
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void rejectAndHangupForbidSessionAndIcePayloads() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.of(sender));

        assertInvalidSignal(CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.REJECT)
                .sdp("v=0")
                .build());
        assertInvalidSignal(CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.HANGUP)
                .candidate("candidate:1")
                .build());

        verify(userRepository, never()).findByIdAndIsDeletedFalseAndStatus(
                RECIPIENT_ID,
                AccountStatus.ACTIVE
        );
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void missingOrUnavailableRecipientIsRejected() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(
                RECIPIENT_ID,
                AccountStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, request(RECIPIENT_ID)),
                ErrorCode.CALL_RECIPIENT_NOT_FOUND
        );

        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void usersCannotSignalThemselves() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(
                SENDER_ID,
                AccountStatus.ACTIVE
        )).thenReturn(Optional.of(sender));

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, request(SENDER_ID)),
                ErrorCode.CALL_SELF_NOT_ALLOWED
        );

        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void pendingFriendshipCannotRelaySignals() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.of(friendship(recipient, sender, FriendshipStatus.PENDING)));

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, request(RECIPIENT_ID)),
                ErrorCode.CALL_REQUIRES_FRIENDSHIP
        );
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void missingFriendshipCannotRelaySignals() {
        User sender = activeUser(SENDER_ID, SENDER_EMAIL);
        User recipient = activeUser(RECIPIENT_ID, RECIPIENT_EMAIL);
        stubSenderAndRecipient(sender, recipient);
        when(friendshipRepository.findByUserPair(RECIPIENT_ID, SENDER_ID))
                .thenReturn(Optional.empty());

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, request(RECIPIENT_ID)),
                ErrorCode.CALL_REQUIRES_FRIENDSHIP
        );
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void inactiveOrUnknownSenderIsUnauthenticated() {
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.empty());

        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, request(RECIPIENT_ID)),
                ErrorCode.UNAUTHENTICATED
        );

        verifyNoInteractions(friendshipRepository);
    }

    private void stubSenderAndRecipient(User sender, User recipient) {
        when(userRepository.findByEmail(SENDER_EMAIL)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdAndIsDeletedFalseAndStatus(
                RECIPIENT_ID,
                AccountStatus.ACTIVE
        )).thenReturn(Optional.of(recipient));
    }

    private CallSignalRequest request(UUID recipientId) {
        return CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(recipientId)
                .type(CallSignalType.HANGUP)
                .build();
    }

    private CallSignalRequest offerRequest() {
        return CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.OFFER)
                .sdp("v=0")
                .build();
    }

    private void stubOfferAllowed() {
        when(rateLimiterService.isAllowed(
                "rate_limit:call:offer:sender:" + SENDER_ID,
                10,
                Duration.ofMinutes(1)
        )).thenReturn(true);
        when(rateLimiterService.isAllowed(
                "rate_limit:call:offer:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                4,
                Duration.ofMinutes(1)
        )).thenReturn(true);
    }

    private void stubSignalAllowed() {
        when(rateLimiterService.isAllowed(
                "rate_limit:call:signal:sender:" + SENDER_ID,
                300,
                Duration.ofMinutes(1)
        )).thenReturn(true);
        when(rateLimiterService.isAllowed(
                "rate_limit:call:signal:pair:" + SENDER_ID + ":" + RECIPIENT_ID,
                200,
                Duration.ofMinutes(1)
        )).thenReturn(true);
    }

    private CallSignalRequest iceRequest(
            String candidate,
            String sdpMid,
            Integer sdpMLineIndex
    ) {
        return CallSignalRequest.builder()
                .callId(CALL_ID)
                .recipientUserId(RECIPIENT_ID)
                .type(CallSignalType.ICE_CANDIDATE)
                .candidate(candidate)
                .sdpMid(sdpMid)
                .sdpMLineIndex(sdpMLineIndex)
                .build();
    }

    private void assertInvalidSignal(CallSignalRequest request) {
        assertError(
                () -> callSignalingService.relaySignal(SENDER_EMAIL, request),
                ErrorCode.INVALID_CALL_SIGNAL
        );
    }

    private Friendship friendship(User low, User high, FriendshipStatus status) {
        return Friendship.builder()
                .userLow(low)
                .userHigh(high)
                .requester(low)
                .status(status)
                .build();
    }

    private User activeUser(UUID id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(email.substring(0, email.indexOf('@')))
                .pinCode("RML-123456")
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            ErrorCode errorCode
    ) {
        assertThatThrownBy(operation)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }

    private static UUID id(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}

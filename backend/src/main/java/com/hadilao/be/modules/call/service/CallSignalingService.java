package com.hadilao.be.modules.call.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.auth.service.RateLimiterService;
import com.hadilao.be.modules.call.dto.CallSignalDTO;
import com.hadilao.be.modules.call.dto.CallSignalDelivery;
import com.hadilao.be.modules.call.dto.CallSignalRequest;
import com.hadilao.be.modules.call.enums.CallSignalType;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CallSignalingService {

    private static final int MAX_SDP_LENGTH = 12 * 1024;
    private static final int MAX_ICE_CANDIDATE_LENGTH = 4 * 1024;
    private static final int MAX_SDP_MID_LENGTH = 256;
    private static final int MAX_SDP_M_LINE_INDEX = 65_535;
    private static final int MAX_SIGNALS_PER_SENDER = 300;
    private static final int MAX_SIGNALS_PER_PAIR = 200;
    private static final int MAX_OFFERS_PER_SENDER = 10;
    private static final int MAX_OFFERS_PER_PAIR = 4;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final RateLimiterService rateLimiterService;

    @Transactional(readOnly = true)
    public CallSignalDelivery relaySignal(String senderUsername, CallSignalRequest request) {
        User sender = requireActiveSender(senderUsername);
        validateRequest(request);

        User recipient = userRepository
                .findByIdAndIsDeletedFalseAndStatus(
                        request.getRecipientUserId(),
                        AccountStatus.ACTIVE
                )
                .orElseThrow(() -> new AppException(ErrorCode.CALL_RECIPIENT_NOT_FOUND));
        if (sender.getId().equals(recipient.getId())) {
            throw new AppException(ErrorCode.CALL_SELF_NOT_ALLOWED);
        }

        UserPair pair = canonicalPair(sender, recipient);
        Friendship friendship = friendshipRepository
                .findByUserPair(pair.low().getId(), pair.high().getId())
                .orElseThrow(() -> new AppException(ErrorCode.CALL_REQUIRES_FRIENDSHIP));
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new AppException(ErrorCode.CALL_REQUIRES_FRIENDSHIP);
        }
        enforceSignalRateLimit(sender, recipient);
        enforceOfferRateLimit(sender, recipient, request.getType());

        CallSignalDTO signal = CallSignalDTO.builder()
                .callId(request.getCallId())
                .senderUserId(sender.getId())
                .recipientUserId(recipient.getId())
                .type(request.getType())
                .sdp(request.getSdp())
                .candidate(request.getCandidate())
                .sdpMid(request.getSdpMid())
                .sdpMLineIndex(request.getSdpMLineIndex())
                .build();
        return new CallSignalDelivery(signal, recipient.getEmail());
    }

    private void enforceSignalRateLimit(User sender, User recipient) {
        boolean senderAllowed = rateLimiterService.isAllowed(
                "rate_limit:call:signal:sender:" + sender.getId(),
                MAX_SIGNALS_PER_SENDER,
                RATE_LIMIT_WINDOW
        );
        boolean pairAllowed = rateLimiterService.isAllowed(
                "rate_limit:call:signal:pair:" + sender.getId() + ":" + recipient.getId(),
                MAX_SIGNALS_PER_PAIR,
                RATE_LIMIT_WINDOW
        );
        if (!senderAllowed || !pairAllowed) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private void enforceOfferRateLimit(User sender, User recipient, CallSignalType signalType) {
        if (signalType != CallSignalType.OFFER) {
            return;
        }

        boolean senderAllowed = rateLimiterService.isAllowed(
                "rate_limit:call:offer:sender:" + sender.getId(),
                MAX_OFFERS_PER_SENDER,
                RATE_LIMIT_WINDOW
        );
        boolean pairAllowed = rateLimiterService.isAllowed(
                "rate_limit:call:offer:pair:" + sender.getId() + ":" + recipient.getId(),
                MAX_OFFERS_PER_PAIR,
                RATE_LIMIT_WINDOW
        );
        if (!senderAllowed || !pairAllowed) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private User requireActiveSender(String username) {
        if (username == null || username.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByEmail(username)
                .filter(this::isAvailable)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private void validateRequest(CallSignalRequest request) {
        if (request == null
                || request.getCallId() == null
                || request.getRecipientUserId() == null
                || request.getType() == null) {
            throw new AppException(ErrorCode.INVALID_CALL_SIGNAL);
        }

        switch (request.getType()) {
            case OFFER, ANSWER -> validateSessionDescription(request);
            case ICE_CANDIDATE -> validateIceCandidate(request);
            case REJECT, HANGUP -> validateTerminalSignal(request);
        }
    }

    private void validateSessionDescription(CallSignalRequest request) {
        if (!isNonBlankWithin(request.getSdp(), MAX_SDP_LENGTH)
                || hasIceFields(request)) {
            throw new AppException(ErrorCode.INVALID_CALL_SIGNAL);
        }
    }

    private void validateIceCandidate(CallSignalRequest request) {
        Integer lineIndex = request.getSdpMLineIndex();
        if (request.getSdp() != null
                || !isNonBlankWithin(request.getCandidate(), MAX_ICE_CANDIDATE_LENGTH)
                || (request.getSdpMid() != null
                && !isNonBlankWithin(request.getSdpMid(), MAX_SDP_MID_LENGTH))
                || (lineIndex != null
                && (lineIndex < 0 || lineIndex > MAX_SDP_M_LINE_INDEX))) {
            throw new AppException(ErrorCode.INVALID_CALL_SIGNAL);
        }
    }

    private void validateTerminalSignal(CallSignalRequest request) {
        if (request.getSdp() != null || hasIceFields(request)) {
            throw new AppException(ErrorCode.INVALID_CALL_SIGNAL);
        }
    }

    private boolean hasIceFields(CallSignalRequest request) {
        return request.getCandidate() != null
                || request.getSdpMid() != null
                || request.getSdpMLineIndex() != null;
    }

    private boolean isNonBlankWithin(String value, int maximumLength) {
        return value != null && !value.isBlank() && value.length() <= maximumLength;
    }

    private boolean isAvailable(User user) {
        return user != null && !user.isDeleted() && user.getStatus() == AccountStatus.ACTIVE;
    }

    private UserPair canonicalPair(User first, User second) {
        if (first.getId().toString().compareTo(second.getId().toString()) < 0) {
            return new UserPair(first, second);
        }
        return new UserPair(second, first);
    }

    private record UserPair(User low, User high) {
    }
}

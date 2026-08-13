package com.hadilao.be.modules.call.controller;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.call.dto.CallErrorDTO;
import com.hadilao.be.modules.call.dto.CallSignalDTO;
import com.hadilao.be.modules.call.dto.CallSignalDelivery;
import com.hadilao.be.modules.call.dto.CallSignalRequest;
import com.hadilao.be.modules.call.enums.CallSignalType;
import com.hadilao.be.modules.call.service.CallSignalingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallWebSocketControllerTest {

    private static final String SENDER = "sender@example.com";
    private static final String RECIPIENT = "recipient@example.com";

    @Mock
    private CallSignalingService callSignalingService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Principal principal;

    @InjectMocks
    private CallWebSocketController controller;

    @Test
    void successfulSignalIsRelayedOnlyToTheRecipientPrivateQueue() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        CallSignalRequest request = CallSignalRequest.builder()
                .callId(UUID.randomUUID())
                .recipientUserId(recipientId)
                .type(CallSignalType.OFFER)
                .sdp("offer-sdp")
                .build();
        CallSignalDTO signal = CallSignalDTO.builder()
                .callId(request.getCallId())
                .senderUserId(senderId)
                .recipientUserId(recipientId)
                .type(CallSignalType.OFFER)
                .sdp("offer-sdp")
                .build();
        when(principal.getName()).thenReturn(SENDER);
        when(callSignalingService.relaySignal(SENDER, request))
                .thenReturn(new CallSignalDelivery(signal, RECIPIENT));

        controller.relaySignal(request, principal);

        verify(messagingTemplate).convertAndSendToUser(
                RECIPIENT,
                "/queue/call-signals",
                signal
        );
        verify(messagingTemplate, never()).convertAndSendToUser(
                eq(SENDER),
                eq("/queue/call-signals"),
                any()
        );
        verify(messagingTemplate, never()).convertAndSendToUser(
                anyString(),
                eq("/queue/call-errors"),
                any()
        );
    }

    @Test
    void businessFailureIsReturnedOnlyToTheSenderWithTheCallId() {
        CallSignalRequest request = CallSignalRequest.builder()
                .callId(UUID.randomUUID())
                .recipientUserId(UUID.randomUUID())
                .type(CallSignalType.OFFER)
                .sdp("offer-sdp")
                .build();
        when(principal.getName()).thenReturn(SENDER);
        when(callSignalingService.relaySignal(SENDER, request))
                .thenThrow(new AppException(ErrorCode.CALL_REQUIRES_FRIENDSHIP));

        controller.relaySignal(request, principal);

        ArgumentCaptor<CallErrorDTO> errorCaptor = ArgumentCaptor.forClass(CallErrorDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(SENDER),
                eq("/queue/call-errors"),
                errorCaptor.capture()
        );
        assertThat(errorCaptor.getValue()).satisfies(error -> {
            assertThat(error.getCallId()).isEqualTo(request.getCallId());
            assertThat(error.getErrorCode()).isEqualTo("CALL_REQUIRES_FRIENDSHIP");
            assertThat(error.getMessage()).isEqualTo("Only accepted friends can call each other");
        });
        verify(messagingTemplate, never()).convertAndSendToUser(
                anyString(),
                eq("/queue/call-signals"),
                any()
        );
    }

    @Test
    void offerRateLimitFailureIsReturnedToTheSenderCallErrorQueue() {
        CallSignalRequest request = CallSignalRequest.builder()
                .callId(UUID.randomUUID())
                .recipientUserId(UUID.randomUUID())
                .type(CallSignalType.OFFER)
                .sdp("offer-sdp")
                .build();
        when(principal.getName()).thenReturn(SENDER);
        when(callSignalingService.relaySignal(SENDER, request))
                .thenThrow(new AppException(ErrorCode.RATE_LIMIT_EXCEEDED));

        controller.relaySignal(request, principal);

        ArgumentCaptor<CallErrorDTO> errorCaptor = ArgumentCaptor.forClass(CallErrorDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(SENDER),
                eq("/queue/call-errors"),
                errorCaptor.capture()
        );
        assertThat(errorCaptor.getValue()).satisfies(error -> {
            assertThat(error.getCallId()).isEqualTo(request.getCallId());
            assertThat(error.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(error.getMessage()).isEqualTo("Rate limit exceeded");
        });
        verify(messagingTemplate, never()).convertAndSendToUser(
                anyString(),
                eq("/queue/call-signals"),
                any()
        );
    }

    @Test
    void nullRequestFailureStillReturnsAWellFormedError() {
        when(principal.getName()).thenReturn(SENDER);
        when(callSignalingService.relaySignal(SENDER, null))
                .thenThrow(new AppException(ErrorCode.INVALID_CALL_SIGNAL));

        controller.relaySignal(null, principal);

        ArgumentCaptor<CallErrorDTO> errorCaptor = ArgumentCaptor.forClass(CallErrorDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(SENDER),
                eq("/queue/call-errors"),
                errorCaptor.capture()
        );
        assertThat(errorCaptor.getValue().getCallId()).isNull();
        assertThat(errorCaptor.getValue().getErrorCode()).isEqualTo("INVALID_CALL_SIGNAL");
    }

    @Test
    void unexpectedFrameFailureUsesTheGenericCallValidationError() {
        CallErrorDTO error = controller.handleUnexpectedMessage(
                new IllegalStateException("malformed frame")
        );

        assertThat(error.getCallId()).isNull();
        assertThat(error.getErrorCode()).isEqualTo("INVALID_CALL_SIGNAL");
        assertThat(error.getMessage()).isEqualTo("Call signal is invalid");
    }
}

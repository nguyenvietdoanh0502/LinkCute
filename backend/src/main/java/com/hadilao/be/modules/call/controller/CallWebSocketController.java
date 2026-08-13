package com.hadilao.be.modules.call.controller;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.call.dto.CallErrorDTO;
import com.hadilao.be.modules.call.dto.CallSignalDelivery;
import com.hadilao.be.modules.call.dto.CallSignalRequest;
import com.hadilao.be.modules.call.service.CallSignalingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CallWebSocketController {

    private static final String SIGNAL_QUEUE = "/queue/call-signals";
    private static final String ERROR_QUEUE = "/queue/call-errors";

    private final CallSignalingService callSignalingService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/call.signal")
    public void relaySignal(CallSignalRequest request, Principal principal) {
        try {
            CallSignalDelivery delivery = callSignalingService.relaySignal(
                    principal.getName(),
                    request
            );
            messagingTemplate.convertAndSendToUser(
                    delivery.recipientUsername(),
                    SIGNAL_QUEUE,
                    delivery.signal()
            );
        } catch (AppException exception) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    ERROR_QUEUE,
                    CallErrorDTO.builder()
                            .callId(request == null ? null : request.getCallId())
                            .errorCode(exception.getErrorCode().getCode())
                            .message(exception.getErrorCode().getMessage())
                            .build()
            );
        }
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(ERROR_QUEUE)
    public CallErrorDTO handleUnexpectedMessage(Exception exception) {
        log.warn("Could not handle a call signaling WebSocket frame", exception);
        ErrorCode error = ErrorCode.INVALID_CALL_SIGNAL;
        return CallErrorDTO.builder()
                .errorCode(error.getCode())
                .message(error.getMessage())
                .build();
    }
}

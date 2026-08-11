package com.hadilao.be.modules.chat.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.chat.dto.ChatHistoryDTO;
import com.hadilao.be.modules.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping(UrlConstant.Chat.FRIEND_MESSAGES)
    public ResponseEntity<ApiResponse<ChatHistoryDTO>> getHistory(
            Principal principal,
            @PathVariable UUID friendId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(required = false) UUID beforeId,
            @RequestParam(defaultValue = "50") Integer size) {
        return ResponseEntity.ok(ApiResponse.success(
                chatService.getHistory(principal.getName(), friendId, before, beforeId, size)
        ));
    }
}

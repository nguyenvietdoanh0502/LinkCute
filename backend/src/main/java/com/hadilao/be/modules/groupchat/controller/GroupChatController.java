package com.hadilao.be.modules.groupchat.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant.GroupChat;
import com.hadilao.be.modules.groupchat.dto.GroupChatDTO.*;
import com.hadilao.be.modules.groupchat.service.GroupChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.*;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class GroupChatController {
    private final GroupChatService service;
    @GetMapping(GroupChat.BASE)
    public ApiResponse<List<Summary>> overview(Principal principal) {
        return ApiResponse.success(service.overview(principal.getName()));
    }
    @PostMapping(GroupChat.BASE)
    public ApiResponse<Detail> create(Principal principal, @Valid @RequestBody NameRequest request) {
        return ApiResponse.success(service.create(principal.getName(), request));
    }
    @GetMapping(GroupChat.BY_ID)
    public ApiResponse<Detail> get(Principal principal, @PathVariable UUID groupId) {
        return ApiResponse.success(service.get(principal.getName(), groupId));
    }
    @PatchMapping(GroupChat.BY_ID)
    public ApiResponse<Detail> rename(Principal principal, @PathVariable UUID groupId, @Valid @RequestBody NameRequest request) {
        return ApiResponse.success(service.rename(principal.getName(), groupId, request));
    }
    @PostMapping(value = GroupChat.AVATAR, consumes = "multipart/form-data")
    public ApiResponse<Detail> avatar(Principal principal, @PathVariable UUID groupId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.avatar(principal.getName(), groupId, file));
    }
    @PostMapping(GroupChat.INVITATIONS)
    public ApiResponse<Detail> invite(Principal principal, @PathVariable UUID groupId, @Valid @RequestBody InviteRequest request) {
        return ApiResponse.success(service.invite(principal.getName(), groupId, request));
    }
    @PostMapping(GroupChat.ACCEPT)
    public ApiResponse<Detail> accept(Principal principal, @PathVariable UUID groupId) {
        return ApiResponse.success(service.accept(principal.getName(), groupId));
    }
    @DeleteMapping(GroupChat.INVITATION)
    public ApiResponse<Void> decline(Principal principal, @PathVariable UUID groupId) {
        service.decline(principal.getName(), groupId);
        return ApiResponse.success(null);
    }
    @DeleteMapping(GroupChat.MEMBERSHIP)
    public ApiResponse<Void> leave(Principal principal, @PathVariable UUID groupId) {
        service.leave(principal.getName(), groupId);
        return ApiResponse.success(null);
    }
    @GetMapping(GroupChat.MESSAGES)
    public ApiResponse<History> history(Principal principal, @PathVariable UUID groupId,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Long after) {
        return ApiResponse.success(service.history(principal.getName(), groupId, before, after));
    }
    @PostMapping(GroupChat.MESSAGES)
    public ApiResponse<Message> send(Principal principal, @PathVariable UUID groupId, @Valid @RequestBody MessageRequest request) {
        return ApiResponse.success(service.send(principal.getName(), groupId, request));
    }
}

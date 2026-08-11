package com.hadilao.be.modules.friendship.controller;

import com.hadilao.be.core.common.ApiResponse;
import com.hadilao.be.core.common.annotation.RestApiV1;
import com.hadilao.be.core.constant.UrlConstant;
import com.hadilao.be.modules.friendship.dto.FriendDTO;
import com.hadilao.be.modules.friendship.dto.FriendRequestDTO;
import com.hadilao.be.modules.friendship.dto.FriendSearchDTO;
import com.hadilao.be.modules.friendship.dto.SendFriendRequestRequest;
import com.hadilao.be.modules.friendship.service.FriendshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RestApiV1
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @GetMapping(UrlConstant.Friend.BASE)
    public ResponseEntity<ApiResponse<List<FriendDTO>>> getFriends() {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getFriends()));
    }

    @GetMapping(UrlConstant.Friend.SEARCH)
    public ResponseEntity<ApiResponse<FriendSearchDTO>> searchByPinCode(
            @RequestParam(required = false) String pinCode) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.searchByPinCode(pinCode)));
    }

    @PostMapping(UrlConstant.Friend.REQUESTS)
    public ResponseEntity<ApiResponse<FriendRequestDTO>> sendRequest(
            @Valid @RequestBody SendFriendRequestRequest request) {
        FriendRequestDTO response = friendshipService.sendRequest(request.getAddresseeId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Friend request sent", response));
    }

    @GetMapping(UrlConstant.Friend.INCOMING_REQUESTS)
    public ResponseEntity<ApiResponse<List<FriendRequestDTO>>> getIncomingRequests() {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getIncomingRequests()));
    }

    @GetMapping(UrlConstant.Friend.OUTGOING_REQUESTS)
    public ResponseEntity<ApiResponse<List<FriendRequestDTO>>> getOutgoingRequests() {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getOutgoingRequests()));
    }

    @PostMapping(UrlConstant.Friend.ACCEPT_REQUEST)
    public ResponseEntity<ApiResponse<FriendDTO>> acceptRequest(@PathVariable("id") UUID requestId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Friend request accepted",
                friendshipService.acceptRequest(requestId)));
    }

    @DeleteMapping(UrlConstant.Friend.REQUEST_BY_ID)
    public ResponseEntity<ApiResponse<Void>> deleteRequest(@PathVariable("id") UUID requestId) {
        friendshipService.deleteRequest(requestId);
        return ResponseEntity.ok(ApiResponse.success("Friend request removed", null));
    }

    @DeleteMapping(UrlConstant.Friend.FRIENDSHIP_BY_ID)
    public ResponseEntity<ApiResponse<Void>> removeFriend(@PathVariable UUID friendshipId) {
        friendshipService.removeFriend(friendshipId);
        return ResponseEntity.ok(ApiResponse.success("Friendship removed", null));
    }
}

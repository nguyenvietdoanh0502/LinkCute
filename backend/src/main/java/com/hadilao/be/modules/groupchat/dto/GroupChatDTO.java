package com.hadilao.be.modules.groupchat.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class GroupChatDTO {
    private GroupChatDTO() {}
    public record NameRequest(@NotBlank @Size(max = 100) String name) {}
    public record InviteRequest(@NotNull UUID userId) {}
    public record MessageRequest(@NotNull UUID clientMessageId, @NotBlank @Size(max = 2000) String content) {}
    public record Person(UUID id, String fullName, String avatarUrl) {}
    public record Summary(UUID id, String name, String avatarUrl, UUID ownerId, String status, Person invitedBy) {}
    public record Participant(Person user, String status, Instant joinedAt) {}
    public record Detail(UUID id, String name, String avatarUrl, UUID ownerId, List<Participant> participants) {}
    public record Message(Long id, UUID groupId, UUID clientMessageId, Person sender, String content, Instant createdAt) {}
    public record History(List<Message> messages, boolean hasMore, Long nextBefore) {}
}

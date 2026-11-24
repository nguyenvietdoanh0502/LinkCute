package com.hadilao.be.modules.groupchat.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;
import com.hadilao.be.modules.groupchat.dto.GroupChatDTO.*;
import com.hadilao.be.modules.groupchat.entity.*;
import com.hadilao.be.modules.groupchat.entity.GroupParticipant.Status;
import com.hadilao.be.modules.groupchat.repository.*;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import com.hadilao.be.modules.user.service.AvatarStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupChatService {
    private final ChatGroupRepository groups;
    private final GroupParticipantRepository participants;
    private final GroupMessageRepository messages;
    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final AvatarStorageService avatars;
    private final SimpMessagingTemplate messaging;

    @Transactional(readOnly = true)
    public List<Summary> overview(String username) {
        return participants.findOverview(activeUser(username).getId()).stream().map(p -> {
            ChatGroup g = p.getGroup();
            return new Summary(g.getId(), g.getName(), g.getAvatarUrl(), g.getOwner().getId(),
                    p.getStatus().name(), person(p.getInvitedBy()));
        }).toList();
    }

    @Transactional
    public Detail create(String username, NameRequest request) {
        User owner = activeUser(username);
        ChatGroup group = groups.saveAndFlush(ChatGroup.builder().owner(owner).name(name(request)).build());
        participants.saveAndFlush(GroupParticipant.builder().group(group).user(owner).invitedBy(owner)
                .status(Status.MEMBER).joinedAt(Instant.now()).build());
        changed(group.getId());
        return detail(group);
    }

    @Transactional
    public Detail get(String username, UUID id) {
        User user = activeUser(username);
        ChatGroup group = locked(id);
        member(id, user.getId());
        return detail(group);
    }

    @Transactional
    public Detail rename(String username, UUID id, NameRequest request) {
        User user = activeUser(username);
        ChatGroup group = locked(id);
        owner(group, user);
        group.setName(name(request));
        changed(id);
        return detail(group);
    }

    @Transactional
    public Detail avatar(String username, UUID id, MultipartFile file) {
        User user = activeUser(username);
        ChatGroup group = locked(id);
        owner(group, user);
        // Reuse the validated Cloudinary image pipeline; group UUIDs identify these assets.
        var uploaded = avatars.upload(id, file);
        String previous = group.getAvatarPublicId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { safeDelete(previous); }
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) safeDelete(uploaded.publicId());
            }
        });
        group.setAvatarUrl(uploaded.url());
        group.setAvatarPublicId(uploaded.publicId());
        groups.saveAndFlush(group);
        changed(id);
        return detail(group);
    }

    @Transactional
    public Detail invite(String username, UUID id, InviteRequest request) {
        User inviter = activeUser(username);
        ChatGroup group = locked(id);
        member(id, inviter.getId());
        if (request == null || request.userId() == null || request.userId().equals(inviter.getId())) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        User invitee = users.findByIdAndIsDeletedFalseAndStatus(request.userId(), AccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        requireFriends(inviter.getId(), invitee.getId());
        if (participants.findByGroupIdAndUserId(id, invitee.getId()).isPresent()) {
            throw new AppException(ErrorCode.GROUP_ALREADY_PARTICIPANT);
        }
        participants.saveAndFlush(GroupParticipant.builder().group(group).user(invitee).invitedBy(inviter)
                .status(Status.INVITED).build());
        changed(id);
        return detail(group);
    }

    @Transactional
    public Detail accept(String username, UUID id) {
        User user = activeUser(username);
        ChatGroup group = locked(id);
        GroupParticipant invitation = invitation(id, user.getId());
        member(id, invitation.getInvitedBy().getId());
        if (!available(invitation.getInvitedBy())) throw new AppException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
        requireFriends(user.getId(), invitation.getInvitedBy().getId());
        invitation.setStatus(Status.MEMBER);
        invitation.setJoinedAt(Instant.now());
        participants.saveAndFlush(invitation);
        changed(id);
        return detail(group);
    }

    @Transactional
    public void decline(String username, UUID id) {
        User user = activeUser(username);
        locked(id);
        GroupParticipant invitation = invitation(id, user.getId());
        changed(id); // Include the recipient's other sessions in the invalidation.
        participants.delete(invitation);
    }

    @Transactional
    public void leave(String username, UUID id) {
        User user = activeUser(username);
        ChatGroup group = locked(id);
        GroupParticipant leaving = member(id, user.getId());
        List<GroupParticipant> all = participants.findParticipants(id);
        changed(id);
        List<GroupParticipant> remaining = all.stream().filter(p -> p.getStatus() == Status.MEMBER
                && !p.getUser().getId().equals(user.getId())).sorted(Comparator
                .comparing(GroupParticipant::getJoinedAt).thenComparing(p -> p.getId().toString())).toList();
        if (remaining.isEmpty()) {
            String publicId = group.getAvatarPublicId();
            messages.deleteForGroup(id);
            participants.deleteForGroup(id);
            groups.delete(group);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safeDelete(publicId); }
            });
        } else {
            if (group.getOwner().getId().equals(user.getId())) group.setOwner(remaining.get(0).getUser());
            participants.delete(leaving);
            // Invitations must retain an inviter who is still a member.
            all.stream().filter(p -> p.getStatus() == Status.INVITED && p.getInvitedBy().getId().equals(user.getId()))
                    .forEach(participants::delete);
        }
    }

    @Transactional
    public Message send(String username, UUID id, MessageRequest request) {
        User user = activeUser(username);
        ChatGroup group = locked(id);
        member(id, user.getId());
        String content = request == null || request.content() == null ? "" : request.content().strip();
        if (request == null || request.clientMessageId() == null || content.isEmpty() || content.length() > 2000) {
            throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
        }
        GroupMessage existing = messages.findByGroupIdAndSenderIdAndClientMessageId(id, user.getId(), request.clientMessageId()).orElse(null);
        if (existing != null) {
            if (!existing.getContent().equals(content)) throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
            return message(existing);
        }
        GroupMessage saved = messages.saveAndFlush(GroupMessage.builder().group(group).sender(user)
                .clientMessageId(request.clientMessageId()).content(content).build());
        changed(id);
        return message(saved);
    }

    @Transactional
    public History history(String username, UUID id, Long before, Long after) {
        User user = activeUser(username);
        locked(id);
        member(id, user.getId());
        if ((before != null && after != null) || (before != null && before < 1) || (after != null && after < 0)) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        int size = 50;
        List<GroupMessage> rows = after == null
                ? messages.findHistory(id, before, PageRequest.of(0, size + 1))
                : messages.findUpdates(id, after, PageRequest.of(0, size + 1));
        boolean hasMore = rows.size() > size;
        List<GroupMessage> selected = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
        Long nextBefore = after == null && hasMore ? selected.get(selected.size() - 1).getId() : null;
        if (after == null) Collections.reverse(selected);
        return new History(selected.stream().map(this::message).toList(), hasMore, nextBefore);
    }

    private User activeUser(String username) {
        if (username == null || username.isBlank()) throw new AppException(ErrorCode.UNAUTHENTICATED);
        return users.findByEmail(username).filter(this::available)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
    private boolean available(User user) { return !user.isDeleted() && user.getStatus() == AccountStatus.ACTIVE; }
    private ChatGroup locked(UUID id) {
        return groups.findForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
    }
    private GroupParticipant member(UUID id, UUID userId) {
        return participants.findByGroupIdAndUserId(id, userId).filter(p -> p.getStatus() == Status.MEMBER)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
    }
    private GroupParticipant invitation(UUID id, UUID userId) {
        return participants.findByGroupIdAndUserId(id, userId).filter(p -> p.getStatus() == Status.INVITED)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_INVITATION_NOT_FOUND));
    }
    private void owner(ChatGroup group, User user) {
        member(group.getId(), user.getId());
        if (!group.getOwner().getId().equals(user.getId())) throw new AppException(ErrorCode.UNAUTHORIZED);
    }
    private void requireFriends(UUID first, UUID second) {
        UUID low = first.toString().compareTo(second.toString()) < 0 ? first : second;
        UUID high = low.equals(first) ? second : first;
        friendships.findAcceptedPairForUpdate(low, high)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_REQUIRES_FRIENDSHIP));
    }
    private String name(NameRequest request) {
        String value = request == null || request.name() == null ? "" : request.name().strip();
        if (value.isEmpty() || value.length() > 100) throw new AppException(ErrorCode.INVALID_GROUP_NAME);
        return value;
    }
    private Person person(User user) { return new Person(user.getId(), user.getFullName(), user.getAvatarUrl()); }
    private Detail detail(ChatGroup group) {
        return new Detail(group.getId(), group.getName(), group.getAvatarUrl(), group.getOwner().getId(),
                participants.findParticipants(group.getId()).stream()
                        .map(p -> new Participant(person(p.getUser()), p.getStatus().name(), p.getJoinedAt())).toList());
    }
    private Message message(GroupMessage m) {
        return new Message(m.getId(), m.getGroup().getId(), m.getClientMessageId(), person(m.getSender()), m.getContent(), m.getCreatedAt());
    }
    private void changed(UUID groupId) {
        List<String> recipients = participants.findParticipants(groupId).stream().map(GroupParticipant::getUser)
                .filter(this::available).map(User::getEmail).distinct().toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // Private invalidations carry no message content; every subsequent REST read checks membership again.
                for (String username : recipients) {
                    try { messaging.convertAndSendToUser(username, "/queue/group-updates", Map.of("groupId", groupId)); }
                    catch (RuntimeException exception) { log.warn("Could not notify group update {}", groupId); }
                }
            }
        });
    }
    private void safeDelete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try { avatars.delete(publicId); }
        catch (RuntimeException exception) { log.warn("Could not clean up group image {}", publicId); }
    }
}

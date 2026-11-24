package com.hadilao.be.modules.groupchat;

import com.hadilao.be.core.exception.*;
import com.hadilao.be.modules.auth.service.MailService;
import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.friendship.repository.FriendshipRepository;
import com.hadilao.be.modules.groupchat.dto.GroupChatDTO.*;
import com.hadilao.be.modules.groupchat.repository.*;
import com.hadilao.be.modules.groupchat.service.GroupChatService;
import com.hadilao.be.modules.user.dto.StoredAvatar;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import com.hadilao.be.modules.user.service.AvatarStorageService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:group_chat_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "logging.level.root=WARN", "debug=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupChatIntegrationTest {
    @MockBean MailService mail;
    @MockBean AvatarStorageService avatars;
    @MockBean SimpMessagingTemplate messaging;
    @Autowired GroupChatService service;
    @Autowired UserRepository users;
    @Autowired FriendshipRepository friendships;
    @Autowired ChatGroupRepository groups;
    @Autowired GroupMessageRepository messages;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    private static final AtomicInteger sequence = new AtomicInteger(100000);
    User owner;
    User friend;
    User outsider;
    UUID id;

    @BeforeEach void setup() {
        owner = saveUser(); friend = saveUser(); outsider = saveUser();
        befriend(owner, friend);
        id = service.create(owner.getEmail(), new NameRequest("  Đi chơi  ")).id();
        clearInvocations(messaging);
    }

    @Test void invitationProtectsHistoryAndSupportsDeclineReinviteAcceptAndLeave() {
        assertThat(service.get(owner.getEmail(), id).name()).isEqualTo("Đi chơi");
        service.invite(owner.getEmail(), id, new InviteRequest(friend.getId()));
        assertThat(service.overview(friend.getEmail())).extracting(Summary::status).containsExactly("INVITED");
        denied(() -> service.get(friend.getEmail(), id), ErrorCode.GROUP_NOT_FOUND);
        denied(() -> service.history(friend.getEmail(), id, null, null), ErrorCode.GROUP_NOT_FOUND);
        denied(() -> service.send(friend.getEmail(), id, request("blocked")), ErrorCode.GROUP_NOT_FOUND);
        denied(() -> service.decline(outsider.getEmail(), id), ErrorCode.GROUP_INVITATION_NOT_FOUND);
        service.decline(friend.getEmail(), id);
        assertThat(service.overview(friend.getEmail())).isEmpty();
        service.invite(owner.getEmail(), id, new InviteRequest(friend.getId()));
        service.accept(friend.getEmail(), id);
        service.send(owner.getEmail(), id, request("Xin chào"));
        assertThat(service.history(friend.getEmail(), id, null, null).messages()).extracting(Message::content).containsExactly("Xin chào");
        service.leave(friend.getEmail(), id);
        assertThat(service.overview(friend.getEmail())).isEmpty();
        denied(() -> service.send(friend.getEmail(), id, request("left")), ErrorCode.GROUP_NOT_FOUND);
        denied(() -> service.history(friend.getEmail(), id, null, null), ErrorCode.GROUP_NOT_FOUND);
    }

    @Test void onlyFriendsCanBeInvitedAndFriendshipIsRecheckedOnAccept() {
        denied(() -> service.invite(owner.getEmail(), id, new InviteRequest(outsider.getId())), ErrorCode.GROUP_REQUIRES_FRIENDSHIP);
        service.invite(owner.getEmail(), id, new InviteRequest(friend.getId()));
        denied(() -> service.invite(owner.getEmail(), id, new InviteRequest(friend.getId())), ErrorCode.GROUP_ALREADY_PARTICIPANT);
        denied(() -> service.invite(owner.getEmail(), id, new InviteRequest(owner.getId())), ErrorCode.INVALID_INPUT);
        List<User> pair = ordered(owner, friend);
        friendships.delete(friendships.findByUserPair(pair.get(0).getId(), pair.get(1).getId()).orElseThrow());
        denied(() -> service.accept(friend.getEmail(), id), ErrorCode.GROUP_REQUIRES_FRIENDSHIP);
        service.decline(friend.getEmail(), id);
        assertThat(service.overview(friend.getEmail())).isEmpty();
    }

    @Test void aMemberCanInviteTheirOwnFriendAndLeavingCancelsTheirPendingInvitations() {
        joinFriend(); befriend(friend, outsider);
        service.invite(friend.getEmail(), id, new InviteRequest(outsider.getId()));
        assertThat(service.overview(outsider.getEmail())).hasSize(1);
        service.leave(friend.getEmail(), id);
        assertThat(service.overview(outsider.getEmail())).isEmpty();
        denied(() -> service.accept(outsider.getEmail(), id), ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }

    @Test void onlyOwnerCanManageNameAndCloudinaryImageAndReplacementDeletesOldAsset() {
        joinFriend();
        MockMultipartFile file = image();
        denied(() -> service.rename(friend.getEmail(), id, new NameRequest("Hacked")), ErrorCode.UNAUTHORIZED);
        denied(() -> service.avatar(friend.getEmail(), id, file), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(avatars);
        assertThat(service.rename(owner.getEmail(), id, new NameRequest("  Nhóm Hà Nội  ")).name()).isEqualTo("Nhóm Hà Nội");
        when(avatars.upload(id, file)).thenReturn(new StoredAvatar("https://res.cloudinary.com/test/one.png", "one"),
                new StoredAvatar("https://res.cloudinary.com/test/two.png", "two"));
        service.avatar(owner.getEmail(), id, file);
        assertThat(service.avatar(owner.getEmail(), id, file).avatarUrl()).endsWith("two.png");
        verify(avatars).delete("one");
        verify(avatars, never()).delete("two");
    }

    @Test void avatarRollbackDeletesNewAssetAndKeepsPreviousImage() {
        MockMultipartFile file = image();
        when(avatars.upload(id, file)).thenReturn(new StoredAvatar("https://res.cloudinary.com/test/new.png", "rollback-image"));
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> { service.avatar(owner.getEmail(), id, file); status.setRollbackOnly(); });
        assertThat(service.get(owner.getEmail(), id).avatarUrl()).isNull();
        verify(avatars).delete("rollback-image");
        verifyNoInteractions(messaging);
    }

    @Test void uploadFailureDoesNotChangeGroup() {
        when(avatars.upload(eq(id), any())).thenThrow(new AppException(ErrorCode.AVATAR_UPLOAD_FAILED));
        denied(() -> service.avatar(owner.getEmail(), id, image()), ErrorCode.AVATAR_UPLOAD_FAILED);
        assertThat(service.get(owner.getEmail(), id).avatarUrl()).isNull();
    }

    @Test void ownerLeavesTransfersOwnershipAndLastMemberDeletesGroupWithPendingInvitesAndImage() {
        joinFriend(); befriend(friend, outsider);
        service.invite(friend.getEmail(), id, new InviteRequest(outsider.getId()));
        when(avatars.upload(eq(id), any())).thenReturn(new StoredAvatar("https://res.cloudinary.com/test/group.png", "group-image"));
        service.avatar(owner.getEmail(), id, image());
        service.send(owner.getEmail(), id, request("Before leaving"));
        service.leave(owner.getEmail(), id);
        assertThat(service.get(friend.getEmail(), id).ownerId()).isEqualTo(friend.getId());
        service.rename(friend.getEmail(), id, new NameRequest("New owner"));
        denied(() -> service.get(owner.getEmail(), id), ErrorCode.GROUP_NOT_FOUND);
        service.leave(friend.getEmail(), id);
        assertThat(groups.findById(id)).isEmpty();
        assertThat(service.overview(outsider.getEmail())).isEmpty();
        verify(avatars).delete("group-image");
    }

    @Test void messagesAreIdempotentAndHaveStableHistoryAndCatchUpCursors() {
        MessageRequest first = request("  First  ");
        Message sent = service.send(owner.getEmail(), id, first);
        assertThat(service.send(owner.getEmail(), id, first).id()).isEqualTo(sent.id());
        denied(() -> service.send(owner.getEmail(), id, new MessageRequest(first.clientMessageId(), "Different")), ErrorCode.INVALID_CHAT_MESSAGE);
        for (int i = 0; i < 55; i++) service.send(owner.getEmail(), id, request("message " + i));
        History latest = service.history(owner.getEmail(), id, null, null);
        assertThat(latest.messages()).hasSize(50);
        assertThat(latest.hasMore()).isTrue();
        History older = service.history(owner.getEmail(), id, latest.nextBefore(), null);
        assertThat(older.messages()).hasSize(6);
        assertThat(older.messages().get(0).content()).isEqualTo("First");
        assertThat(older.hasMore()).isFalse();
        History catchup = service.history(owner.getEmail(), id, null, sent.id());
        assertThat(catchup.messages()).hasSize(50);
        assertThat(catchup.hasMore()).isTrue();
        History tail = service.history(owner.getEmail(), id, null, catchup.messages().get(49).id());
        assertThat(tail.messages()).hasSize(5);
        assertThat(tail.hasMore()).isFalse();
        denied(() -> service.history(owner.getEmail(), id, 1L, 1L), ErrorCode.INVALID_INPUT);
    }

    @Test void concurrentRetriesCreateOneMessage() throws Exception {
        MessageRequest request = request("Concurrent");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Message> send = () -> { start.await(); return service.send(owner.getEmail(), id, request); };
            Future<Message> one = pool.submit(send); Future<Message> two = pool.submit(send);
            start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS).id()).isEqualTo(two.get(10, TimeUnit.SECONDS).id());
            assertThat(service.history(owner.getEmail(), id, null, null).messages()).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void privateNotificationsContainOnlyGroupIdAndArePublishedAfterCommit() {
        service.invite(owner.getEmail(), id, new InviteRequest(friend.getId()));
        verify(messaging).convertAndSendToUser(owner.getEmail(), "/queue/group-updates", Map.of("groupId", id));
        verify(messaging).convertAndSendToUser(friend.getEmail(), "/queue/group-updates", Map.of("groupId", id));
        verify(messaging, never()).convertAndSendToUser(eq(outsider.getEmail()), anyString(), any(Object.class));
    }

    @Test void invalidInputAndInactiveAccountsAreRejected() {
        denied(() -> service.create(owner.getEmail(), new NameRequest("  ")), ErrorCode.INVALID_GROUP_NAME);
        denied(() -> service.rename(owner.getEmail(), id, new NameRequest("x".repeat(101))), ErrorCode.INVALID_GROUP_NAME);
        denied(() -> service.send(owner.getEmail(), id, request("  ")), ErrorCode.INVALID_CHAT_MESSAGE);
        friend.setStatus(AccountStatus.BANNED); users.saveAndFlush(friend);
        denied(() -> service.invite(owner.getEmail(), id, new InviteRequest(friend.getId())), ErrorCode.USER_NOT_EXISTED);
        denied(() -> service.overview(friend.getEmail()), ErrorCode.UNAUTHENTICATED);
    }

    @Test void restRequiresAuthenticationValidatesInputAndKeepsMembershipPrivate() throws Exception {
        String base = "/api/v1/chat/groups";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(post(base).with(user(owner.getEmail())).contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base + "/" + id).with(user(outsider.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/" + id + "/invitations").with(user(owner.getEmail()))
                .contentType("application/json").content("{\"userId\":\"" + friend.getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.participants.length()").value(2));
        mvc.perform(get(base + "/" + id + "/messages").with(user(friend.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/" + id + "/invitation/accept").with(user(friend.getEmail()))).andExpect(status().isOk());
        mvc.perform(post(base + "/" + id + "/messages").with(user(friend.getEmail())).contentType("application/json")
                .content("{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"content\":\"Hello group\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sender.id").value(friend.getId().toString()));
        mvc.perform(delete(base + "/" + id + "/membership").with(user(friend.getEmail()))).andExpect(status().isOk());
        mvc.perform(get(base + "/" + id + "/messages").with(user(friend.getEmail()))).andExpect(status().isNotFound());
    }

    private void joinFriend() { service.invite(owner.getEmail(), id, new InviteRequest(friend.getId())); service.accept(friend.getEmail(), id); }
    private MessageRequest request(String content) { return new MessageRequest(UUID.randomUUID(), content); }
    private MockMultipartFile image() { return new MockMultipartFile("file", "group.png", "image/png", new byte[]{1, 2, 3}); }
    private User saveUser() {
        int n = sequence.incrementAndGet();
        return users.saveAndFlush(User.builder().email("group-" + n + "@test.local").password("test")
                .fullName("User " + n).pinCode(String.valueOf(n)).status(AccountStatus.ACTIVE).build());
    }
    private List<User> ordered(User a, User b) { return List.of(a, b).stream().sorted(Comparator.comparing(u -> u.getId().toString())).toList(); }
    private void befriend(User a, User b) {
        List<User> pair = ordered(a, b);
        friendships.saveAndFlush(Friendship.builder().userLow(pair.get(0)).userHigh(pair.get(1)).requester(a)
                .status(FriendshipStatus.ACCEPTED).acceptedAt(Instant.now()).build());
    }
    private void denied(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}

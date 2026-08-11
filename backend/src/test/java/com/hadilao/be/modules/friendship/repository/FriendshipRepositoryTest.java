package com.hadilao.be.modules.friendship.repository;

import com.hadilao.be.modules.friendship.entity.Friendship;
import com.hadilao.be.modules.friendship.enums.FriendshipStatus;
import com.hadilao.be.modules.user.entity.User;
import com.hadilao.be.modules.user.enums.AccountStatus;
import com.hadilao.be.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:friendship_repository;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class FriendshipRepositoryTest {

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsTheCanonicalPairAndRejectsADuplicatePair() {
        User alice = saveUser("alice@example.com", "Alice", "RML-100001");
        User bob = saveUser("bob@example.com", "Bob", "RML-100002");

        Friendship first = pendingFriendship(alice, bob, alice);
        friendshipRepository.saveAndFlush(first);

        User low = lower(alice, bob);
        User high = other(low, alice, bob);
        assertThat(friendshipRepository.findByUserPair(low.getId(), high.getId()))
                .contains(first);

        Friendship duplicate = pendingFriendship(alice, bob, bob);
        assertThatThrownBy(() -> friendshipRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void separatesIncomingAndOutgoingPendingRequests() {
        User alice = saveUser("alice@example.com", "Alice", "RML-200001");
        User bob = saveUser("bob@example.com", "Bob", "RML-200002");
        User carol = saveUser("carol@example.com", "Carol", "RML-200003");

        Friendship outgoing = friendshipRepository.saveAndFlush(pendingFriendship(alice, bob, alice));
        Friendship incoming = friendshipRepository.saveAndFlush(pendingFriendship(alice, carol, carol));

        assertThat(friendshipRepository.findOutgoingRequests(alice.getId()))
                .extracting(Friendship::getId)
                .containsExactly(outgoing.getId());
        assertThat(friendshipRepository.findIncomingRequests(alice.getId()))
                .extracting(Friendship::getId)
                .containsExactly(incoming.getId());
    }

    @Test
    void listsAcceptedFriendshipsForEitherEndpointOnly() {
        User alice = saveUser("alice@example.com", "Alice", "RML-300001");
        User bob = saveUser("bob@example.com", "Bob", "RML-300002");
        User carol = saveUser("carol@example.com", "Carol", "RML-300003");

        Friendship accepted = pendingFriendship(alice, bob, alice);
        accepted.accept();
        friendshipRepository.saveAndFlush(accepted);
        friendshipRepository.saveAndFlush(pendingFriendship(alice, carol, carol));

        List<Friendship> aliceFriends = friendshipRepository.findAllForUserByStatus(
                alice.getId(),
                FriendshipStatus.ACCEPTED
        );
        List<Friendship> bobFriends = friendshipRepository.findAllForUserByStatus(
                bob.getId(),
                FriendshipStatus.ACCEPTED
        );

        assertThat(aliceFriends).extracting(Friendship::getId).containsExactly(accepted.getId());
        assertThat(bobFriends).extracting(Friendship::getId).containsExactly(accepted.getId());
        assertThat(accepted.getAcceptedAt()).isNotNull();
    }

    private User saveUser(String email, String fullName, String pinCode) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .fullName(fullName)
                .pinCode(pinCode)
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private Friendship pendingFriendship(User first, User second, User requester) {
        User low = lower(first, second);
        User high = other(low, first, second);
        return Friendship.builder()
                .userLow(low)
                .userHigh(high)
                .requester(requester)
                .status(FriendshipStatus.PENDING)
                .build();
    }

    private User lower(User first, User second) {
        return first.getId().toString().compareTo(second.getId().toString()) < 0 ? first : second;
    }

    private User other(User selected, User first, User second) {
        return selected == first ? second : first;
    }
}

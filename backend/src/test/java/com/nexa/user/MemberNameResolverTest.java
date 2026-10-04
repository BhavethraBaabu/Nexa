package com.nexa.user;

import com.nexa.user.MemberNameResolver.Resolution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemberNameResolverTest {

    private static final UUID ORG = UUID.randomUUID();
    private final MemberNameResolver resolver = new MemberNameResolver();

    private final User johnSmith = member("John Smith", "john@acme.com");
    private final User sarahLee = member("Sarah Lee", "sarah.lee@acme.com");
    private final User mikeChen = member("Mike Chen", "mchen@acme.com");
    private final User renee = member("Renée Dubois", "renee@acme.com");
    private final List<User> members = List.of(johnSmith, sarahLee, mikeChen, renee);

    @Test
    void firstNameMatchesTheOnlyMemberWithIt() {
        assertThat(resolver.resolve("John", members)).isEqualTo(new Resolution.Matched(johnSmith));
    }

    @Test
    void fullNameIsCaseAndPunctuationInsensitive() {
        assertThat(resolver.resolve("  sarah LEE. ", members)).isEqualTo(new Resolution.Matched(sarahLee));
    }

    @Test
    void fullEmailMatchesButLocalPartAloneDoesNot() {
        assertThat(resolver.resolve("MChen@acme.com", members)).isEqualTo(new Resolution.Matched(mikeChen));
        assertThat(resolver.resolve("mchen", members)).isEqualTo(new Resolution.NoMatch());
    }

    @Test
    void accentsAreIgnored() {
        assertThat(resolver.resolve("Renee", members)).isEqualTo(new Resolution.Matched(renee));
    }

    @Test
    void sharedFirstNameIsAmbiguousNotGuessed() {
        User johnDoe = member("John Doe", "jdoe@acme.com");
        assertThat(resolver.resolve("John", List.of(johnSmith, johnDoe))).isInstanceOf(Resolution.Ambiguous.class);
        assertThat(resolver.resolveUnique("John", List.of(johnSmith, johnDoe))).isEmpty();
    }

    @Test
    void fullNameWinsOverAmbiguousFirstName() {
        User johnDoe = member("John Doe", "jdoe@acme.com");
        assertThat(resolver.resolve("John Doe", List.of(johnSmith, johnDoe))).isEqualTo(new Resolution.Matched(johnDoe));
    }

    @Test
    void unknownNamesAndRolesDoNotMatch() {
        assertThat(resolver.resolve("Priya", members)).isEqualTo(new Resolution.NoMatch());
        assertThat(resolver.resolve("someone from backend", members)).isEqualTo(new Resolution.NoMatch());
        assertThat(resolver.resolve("John Williams", members)).isEqualTo(new Resolution.NoMatch());
        assertThat(resolver.resolve("", members)).isEqualTo(new Resolution.NoMatch());
        assertThat(resolver.resolve(null, members)).isEqualTo(new Resolution.NoMatch());
    }

    @Test
    void disabledMembersAreIgnored() {
        User former = member("Olga Ivanova", "olga@acme.com");
        former.disable(Instant.now());
        assertThat(resolver.resolve("Olga", List.of(former))).isEqualTo(new Resolution.NoMatch());
    }

    private static User member(String name, String email) {
        return User.create(ORG, name, email, "{noop}x", Role.MEMBER, Instant.now());
    }
}

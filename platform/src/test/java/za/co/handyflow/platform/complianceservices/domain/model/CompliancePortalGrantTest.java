package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompliancePortalGrantTest {

    private static final Instant NOW = Instant.parse("2027-03-01T08:00:00Z");
    private static final UUID STAFF = UUID.randomUUID();

    private static CompliancePortalGrant invite() {
        return CompliancePortalGrant.invite(UUID.randomUUID(), UUID.randomUUID(), "  Jo@Acme.co.za ", STAFF, NOW);
    }

    @Test
    @DisplayName("An invite is pending, has a long random token and expires in seven days")
    void inviting() {
        CompliancePortalGrant g = invite();
        assertThat(g.getStatus()).isEqualTo("PENDING");
        assertThat(g.getInviteEmail()).isEqualTo("jo@acme.co.za");
        assertThat(g.getInviteToken().length() >= 40).isTrue();
        assertThat(g.getInviteTokenExpiresAt()).isEqualTo(NOW.plusSeconds(7L * 24 * 3600));
        assertThat(invite().getInviteToken().equals(g.getInviteToken())).isFalse();
        assertThat(g.isInviteValid(NOW)).isTrue();
        assertThat(g.isActive()).isFalse();
    }

    @Test
    @DisplayName("A bad email is refused")
    void badEmail() {
        assertThatThrownBy(() -> CompliancePortalGrant.invite(UUID.randomUUID(), UUID.randomUUID(), "nope", STAFF, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("An invite stops working once it has expired")
    void expiry() {
        CompliancePortalGrant g = invite();
        assertThat(g.isInviteValid(NOW.plusSeconds(7L * 24 * 3600 - 1))).isTrue();
        assertThat(g.isInviteValid(NOW.plusSeconds(7L * 24 * 3600 + 1))).isFalse();
        assertThatThrownBy(() -> g.accept(UUID.randomUUID(), NOW.plusSeconds(8L * 24 * 3600))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Accepting uses the token up, so the link cannot be used twice")
    void accepting() {
        CompliancePortalGrant g = invite();
        UUID user = UUID.randomUUID();
        g.accept(user, NOW.plusSeconds(60));
        assertThat(g.isActive()).isTrue();
        assertThat(g.getPortalUserId()).isEqualTo(user);
        assertThat(g.getInviteToken()).isNull();
        assertThatThrownBy(() -> g.accept(UUID.randomUUID(), NOW.plusSeconds(120))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invite().accept(null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Revoking ends access and kills a pending token; it cannot be done twice")
    void revoking() {
        CompliancePortalGrant pending = invite();
        pending.revoke(STAFF, NOW);
        assertThat(pending.getStatus()).isEqualTo("REVOKED");
        assertThat(pending.getInviteToken()).isNull();
        assertThat(pending.isInviteValid(NOW)).isFalse();
        assertThatThrownBy(() -> pending.revoke(STAFF, NOW)).isInstanceOf(IllegalStateException.class);
        CompliancePortalGrant active = invite();
        active.accept(UUID.randomUUID(), NOW);
        active.revoke(STAFF, NOW.plusSeconds(10));
        assertThat(active.isActive()).isFalse();
        assertThat(active.getRevokedBy()).isEqualTo(STAFF);
    }
}

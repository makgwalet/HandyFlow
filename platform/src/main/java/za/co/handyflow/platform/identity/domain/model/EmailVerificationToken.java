package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EmailVerificationToken {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static EmailVerificationToken create(UUID userId, UUID tenantId) {
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.userId    = userId;
        evt.tenantId  = tenantId;
        // Same 64-char random-token convention already used by
        // PasswordResetToken and UserInvitation.
        evt.token     = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        // 72 hours — matches UserInvitation's own expiry window, a
        // reasonable amount of time for someone to get around to
        // checking a "welcome, please verify" email that isn't blocking
        // anything they urgently need to do.
        evt.expiresAt = Instant.now().plusSeconds(72 * 3600);
        return evt;
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isValid()   { return !used && !isExpired(); }

    public void markUsed() { this.used = true; }
}

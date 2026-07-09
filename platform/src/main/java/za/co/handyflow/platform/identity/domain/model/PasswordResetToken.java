package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PasswordResetToken {

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

    public static PasswordResetToken create(UUID userId, UUID tenantId) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.userId    = userId;
        prt.tenantId  = tenantId;
        // 64-char secure random token
        prt.token     = UUID.randomUUID().toString().replace("-", "")
                      + UUID.randomUUID().toString().replace("-", "");
        prt.expiresAt = Instant.now().plusSeconds(900); // 1 hour
        return prt;
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isValid()   { return !used && !isExpired(); }

    public void markUsed() { this.used = true; }
}

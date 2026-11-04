package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_invitations")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class UserInvitation {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "job_title")
    private String jobTitle;

    private String department;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    /** SHA-256 hex of the emailed token. The clear token is never stored (V589). */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Clear token, available only on the instance that created or renewed it, to build the emailed link. */
    @Transient
    private String rawToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public enum InvitationStatus { PENDING, ACCEPTED, EXPIRED, CANCELLED }

    public static UserInvitation create(TenantId tenantId, String email,
                                        String firstName, String lastName,
                                        String jobTitle, String department,
                                        Role role, UUID invitedBy) {
        UserInvitation inv = new UserInvitation();
        inv.tenantId   = tenantId;
        inv.email      = email.toLowerCase().trim();
        inv.firstName  = firstName;
        inv.lastName   = lastName;
        inv.jobTitle   = jobTitle;
        inv.department = department;
        inv.role       = role;
        inv.invitedBy  = invitedBy;
        inv.issueToken();
        return inv;
    }

    private void issueToken() {
        this.rawToken  = UUID.randomUUID().toString().replace("-", "") +
                         UUID.randomUUID().toString().replace("-", "");  // 64-char token
        this.tokenHash = hashToken(rawToken);
        this.expiresAt = Instant.now().plusSeconds(72 * 3600); // 72 hours
    }

    /** SHA-256 hex of a presented token. */
    public static String hashToken(String token) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** New token and a fresh 72 hours; a PENDING or EXPIRED invitation becomes PENDING again. */
    public void renew() {
        if (status == InvitationStatus.ACCEPTED || status == InvitationStatus.CANCELLED)
            throw new IllegalStateException("Only pending or expired invitations can be re-sent");
        this.status = InvitationStatus.PENDING;
        issueToken();
    }

    public boolean isExpired()  { return Instant.now().isAfter(expiresAt); }
    public boolean isPending()  { return status == InvitationStatus.PENDING; }
    /** Pending and not past its expiry: the only invitations that hold a seat. */
    public boolean isLive()     { return isPending() && !isExpired(); }

    public void accept() {
        this.status     = InvitationStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }

    public void cancel() {
        this.status = InvitationStatus.CANCELLED;
    }
}

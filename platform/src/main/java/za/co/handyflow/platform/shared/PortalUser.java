package za.co.handyflow.platform.shared;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A client-portal login identity — email/password only, nothing
 * module-specific. Deliberately lives in the shared package, not
 * accountant, since the whole point is that a person who's both an
 * accounting client and (eventually) a recruiter candidate can use one
 * login. What that login can actually DO is entirely governed by each
 * module's own access-grant table (e.g. AccPortalAccessGrant) — this
 * entity has no notion of "which module" or "which client" at all.
 * <p>
 * Password hashing reuses the exact same BCrypt PasswordEncoder bean
 * already defined in SecurityConfig — not a separate hashing scheme
 * for portal users.
 */
@Entity(name = "PortalUser")
@Table(name = "portal_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortalUser {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "email", nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(name = "active", nullable = false) private boolean active = true;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static PortalUser create(String email, String passwordHash, String fullName) {
        PortalUser u = new PortalUser();
        u.email        = email.toLowerCase().trim();
        u.passwordHash = passwordHash;
        u.fullName     = fullName;
        u.createdAt    = Instant.now();
        return u;
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
    }
}
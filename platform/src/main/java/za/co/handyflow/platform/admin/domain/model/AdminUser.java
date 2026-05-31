package za.co.handyflow.platform.admin.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_users")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AdminUser {

    @Id private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true) private String  email;
    @Column(name = "password_hash", nullable = false) private String  passwordHash;
    @Column(name = "full_name",     nullable = false) private String  fullName;
    @Column(nullable = false)                          private String  role = "SUPERADMIN";

    // TOTP
    @Column(name = "totp_secret")      private String  totpSecret;
    @Column(name = "totp_enabled")     private boolean totpEnabled = false;
    @Column(name = "totp_verified_at") private Instant totpVerifiedAt;

    // Session
    @Column(name = "last_login_at")  private Instant lastLoginAt;
    @Column(name = "last_login_ip")  private String  lastLoginIp;
    @Column(name = "failed_attempts") private int    failedAttempts = 0;
    @Column(name = "locked_until")  private Instant lockedUntil;
    @Column(nullable = false)        private boolean active = true;
    @Column(name = "created_at")    private Instant createdAt;
    @Column(name = "updated_at")    private Instant updatedAt;

    public static AdminUser create(String email, String passwordHash,
                                    String fullName, String role) {
        AdminUser u      = new AdminUser();
        u.email          = email.toLowerCase().trim();
        u.passwordHash   = passwordHash;
        u.fullName       = fullName;
        u.role           = role != null ? role : "SUPERADMIN";
        u.totpEnabled    = false;
        u.failedAttempts = 0;
        u.active         = true;
        u.createdAt      = Instant.now();
        u.updatedAt      = Instant.now();
        return u;
    }

    public void recordLogin(String ip) {
        this.lastLoginAt    = Instant.now();
        this.lastLoginIp    = ip;
        this.failedAttempts = 0;
        this.lockedUntil    = null;
        this.updatedAt      = Instant.now();
    }

    public void recordFailedAttempt() {
        this.failedAttempts++;
        if (this.failedAttempts >= 5) {
            this.lockedUntil = Instant.now().plusSeconds(900); // 15 min lockout
        }
        this.updatedAt = Instant.now();
    }

    public void setupTotp(String secret) {
        this.totpSecret  = secret;
        this.totpEnabled = false; // not enabled until first successful verification
        this.updatedAt   = Instant.now();
    }

    public void enableTotp() {
        this.totpEnabled    = true;
        this.totpVerifiedAt = Instant.now();
        this.updatedAt      = Instant.now();
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }

    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }
}

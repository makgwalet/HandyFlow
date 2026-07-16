package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.identity.UserCreatedEvent;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class User extends AggregateRoot<User> {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    // Added by V29 migration
    @Column(name = "phone")
    private String phone;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "department")
    private String department;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    // NEW: deliberately non-blocking — verifying tracks and nudges, it
    // doesn't gate login or app usage. See V_email_verification.sql for
    // the full reasoning.
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    // NEW: opt-in flag for billing communication routing — false by
    // default, since most users at a tenant (a guard supervisor, a
    // clinic receptionist) have no reason to receive the subscription
    // invoice. Only users a tenant admin explicitly designates should
    // get billing comms via this path. See the new BillingRecipientResolver
    // for how this is actually used.
    @Column(name = "receives_billing_comms", nullable = false)
    private boolean receivesBillingComms = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public enum UserStatus {
        ACTIVE, INACTIVE, LOCKED
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public static User create(TenantId tenantId, String email,
                              String passwordHash, String firstName,
                              String lastName) {
        User user = new User();
        user.setTenantIdOnCreation(tenantId);
        user.email        = email.toLowerCase().trim();
        user.passwordHash = passwordHash;
        user.firstName    = firstName;
        user.lastName     = lastName;
        user.status       = UserStatus.ACTIVE;

        user.registerEvent(UserCreatedEvent.of(tenantId, user.getId(), email));
        return user;
    }

    private void setTenantIdOnCreation(TenantId tenantId) {
        initTenantId(tenantId);
    }

    // ── Profile updates ───────────────────────────────────────────────────────

    public void setFirstName(String firstName)   { this.firstName  = firstName; }
    public void setLastName(String lastName)      { this.lastName   = lastName; }
    public void setPhone(String phone)            { this.phone      = phone; }
    public void setJobTitle(String jobTitle)      { this.jobTitle   = jobTitle; }
    public void setDepartment(String department)  { this.department = department; }
    public void setReceivesBillingComms(boolean receivesBillingComms) { this.receivesBillingComms = receivesBillingComms; }

    // ── Role management ───────────────────────────────────────────────────────

    public void assignRole(Role role) {
        this.roles.add(role);
    }

    /** Replace all roles with a single role — used when admin changes a user's role. */
    public void clearRoles() {
        this.roles.clear();
    }

    // ── Status management ─────────────────────────────────────────────────────

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public void verifyEmail() {
        this.emailVerified   = true;
        this.emailVerifiedAt = Instant.now();
    }

    // ── Permission resolution ─────────────────────────────────────────────────

    // WHY expose this method instead of direct field access?
    // Spring Security needs a flat list of authority strings.
    // This method translates our rich domain model into that format.
    public Set<String> getPermissionNames() {
        Set<String> perms = new HashSet<>();
        for (Role role : roles) {
            for (Permission permission : role.getPermissions()) {
                perms.add(permission.getName());
            }
        }
        return perms;
    }
}

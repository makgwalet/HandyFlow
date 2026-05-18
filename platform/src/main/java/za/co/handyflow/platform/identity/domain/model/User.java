package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.identity.UserCreatedEvent;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

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

    public static User create(TenantId tenantId, String email,
                              String passwordHash, String firstName,
                              String lastName) {
        User user = new User();
        user.setTenantIdOnCreation(tenantId);
        user.email = email.toLowerCase().trim();
        user.passwordHash = passwordHash;
        user.firstName = firstName;
        user.lastName = lastName;
        user.status = UserStatus.ACTIVE;

        user.registerEvent(UserCreatedEvent.of(
                tenantId,
                user.getId(),
                email
        ));

        return user;
    }

    // Called by AggregateRoot subclasses that don't self-reference
    private void setTenantIdOnCreation(TenantId tenantId) {
        initTenantId(tenantId);
    }

    public void assignRole(Role role) {
        this.roles.add(role);
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

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

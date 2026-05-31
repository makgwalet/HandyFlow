package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
        column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    @Version
    private Long version;

    public static Role create(TenantId tenantId, String name, String description) {
        Role role = new Role();
        role.tenantId    = tenantId;
        role.name        = name.toUpperCase().trim();
        role.description = description;
        return role;
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    /** Replace all permissions — used when admin edits a role's permission set. */
    public void clearPermissions() {
        this.permissions.clear();
    }
}

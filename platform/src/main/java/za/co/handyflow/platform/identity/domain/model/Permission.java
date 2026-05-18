package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Permission {
    @Id
    private UUID id = UUID.randomUUID();

    // WHY no tenantId on Permission?
    // Permissions are SYSTEM-LEVEL — defined by us, not per-tenant.
    // Examples: USER_READ, INVOICE_CREATE, REPORT_VIEW
    // Every tenant gets the same set of permissions.
    // What differs per-tenant is which ROLES have which permissions.
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    public static Permission of(String name, String description) {
        Permission p = new Permission();
        p.name = name.toUpperCase().trim();
        p.description = description;
        return p;
    }
}

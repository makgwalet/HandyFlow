package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Reusable across every post order at a site, per the product owner's
 * own explicit instruction: "don't put emergency contacts exclusively
 * inside Post Orders... if the client's control-room number changes,
 * you don't have to edit 15 different post orders." Referenced by
 * PostOrder via a join table (PostOrderContactRepository), never
 * duplicated into the post order itself.
 */
@Entity
@Table(name = "security_contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityContact {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id") private UUID siteId; // nullable — a tenant-wide contact (e.g. "Police") isn't scoped to one site
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String role; // SITE_MANAGER | CLIENT_CONTACT | SECURITY_MANAGER | CONTROL_ROOM | POLICE | AMBULANCE | FIRE | OTHER
    private String phone;
    private String email;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static SecurityContact create(TenantId tenantId, UUID siteId, String name, String role,
                                         String phone, String email) {
        SecurityContact c = new SecurityContact();
        c.tenantId = tenantId;
        c.siteId = siteId;
        c.name = name;
        c.role = role;
        c.phone = phone;
        c.email = email;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String name, String role, String phone, String email) {
        this.name = name;
        this.role = role;
        this.phone = phone;
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}

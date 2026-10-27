package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A place stock is held: a storeroom, a van, a site cupboard. Exactly one is the tenant's default. */
@Entity
@Table(name = "facility_stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityStore {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "is_default", nullable = false)
    private boolean defaultStore;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FacilityStore create(TenantId tenantId, String code, String name, UUID siteId, boolean defaultStore) {
        FacilityStore s = new FacilityStore();
        s.tenantId = tenantId;
        s.code = normaliseCode(code);
        s.apply(name, siteId);
        s.defaultStore = defaultStore;
        s.createdAt = Instant.now();
        return s;
    }

    public static String normaliseCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        String c = code.trim().toUpperCase();
        if (!c.matches("[A-Z0-9][A-Z0-9_-]{0,29}"))
            throw new IllegalArgumentException("code must be 1-30 letters, digits, hyphens or underscores");
        return c;
    }

    public void update(String name, UUID siteId) { apply(name, siteId); }

    private void apply(String name, UUID siteId) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (name.trim().length() > 100) throw new IllegalArgumentException("name must be 100 characters or fewer");
        this.name = name.trim();
        this.siteId = siteId;
        this.updatedAt = Instant.now();
    }

    public void makeDefault() {
        if (!active) throw new IllegalStateException("An inactive store cannot be the default");
        this.defaultStore = true;
        this.updatedAt = Instant.now();
    }

    public void clearDefault() { this.defaultStore = false; this.updatedAt = Instant.now(); }

    public void deactivate() {
        if (defaultStore) throw new IllegalStateException("The default store cannot be deactivated; make another store the default first");
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void reactivate() { this.active = true; this.updatedAt = Instant.now(); }
}

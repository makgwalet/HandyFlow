package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.identity.TenantCreatedEvent;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// WHY protected constructor?
// JPA needs a no-arg constructor to instantiate entities via reflection.
// Making it protected prevents accidental direct instantiation —
// force callers to use our factory method which enforces business rules.
public class Tenant extends AggregateRoot<Tenant> {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    // ← NO @Enumerated here
    @Column(nullable = false, unique = true)
    private String email;

    // @Enumerated ONLY on status — nowhere else
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    public enum TenantStatus {
        TRIAL, ACTIVE, SUSPENDED, CANCELLED
    }

    // WHY a static factory method instead of a public constructor?
    // Factory methods have NAMES — "register" tells you the intent.
    // They can enforce business rules before the object exists.
    // They can register domain events as part of creation.
    public static Tenant register(String name, String slug, String email) {
        validateSlug(slug);

        Tenant tenant = new Tenant();

        tenant.initTenantId(TenantId.of(tenant.getId()));
        tenant.name = name;
        tenant.slug = slug.toLowerCase().trim();
        tenant.email = email.toLowerCase().trim();
        tenant.status = TenantStatus.TRIAL;

        // Register domain event — Spring Data publishes this after save()
        tenant.registerEvent(TenantCreatedEvent.of(
                tenant.getTenantId(),
                name,
                email,
                tenant.getId()
        ));

        return tenant;
    }

    public void activate() {
        if (this.status == TenantStatus.CANCELLED){
            throw new IllegalStateException("Cannot activate a cancelled tenant");
        }
        this.status = TenantStatus.ACTIVE;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public boolean isActive() {
        return this.status == TenantStatus.ACTIVE ||
                this.status == TenantStatus.TRIAL;
    }

    private static void validateSlug(String slug) {
        if (slug == null || !slug.matches("^[a-z0-9-]{3,100}$")) {
            throw new IllegalArgumentException(
                    "Slug must be 3-100 characters, lowercase letters, numbers and hyphens only"
            );
        }
    }
}

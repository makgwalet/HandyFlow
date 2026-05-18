package za.co.handyflow.platform.shared;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.Instant;
import java.util.UUID;

/**
 * WHY EXTEND AbstractAggregateRoot?
 *
 * Spring Data's AbstractAggregateRoot gives us the ability to
 * register domain events inside our entities and have Spring
 * automatically publish them after the transaction commits.
 *
 * This means: the event only fires if the DB write SUCCEEDED.
 * No manual event publishing in service classes.
 *
 * Usage in subclass:
 *   registerEvent(new UserCreatedEvent(...));
 *   // Spring publishes this after save() completes
 */
@MappedSuperclass  // WHY: Shared fields for ALL entities without its own table
@Getter
public abstract class AggregateRoot<T extends AggregateRoot<T>>
    extends AbstractAggregateRoot<T> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // WHY: We embed TenantId in every aggregate — this is the
    // foundation of our multi-tenant data isolation strategy.
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false ))
    private TenantId tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version  // WHY: Optimistic locking — prevents lost updates in concurrent requests
    private Long version;

    protected AggregateRoot() {
        this.id = UUID.randomUUID();
        this.createdAt  = Instant.now();
        this.updatedAt = Instant.now();
    }

    protected AggregateRoot(TenantId tenantId) {
        this();
        if (tenantId == null) throw new IllegalArgumentException("TenantId is required");
        this.tenantId = tenantId;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    protected void initTenantId(TenantId tenantId) {
        if (this.tenantId != null) {
            throw new IllegalStateException("TenantId already initialized");
        }
        this.tenantId = tenantId;
    }
}

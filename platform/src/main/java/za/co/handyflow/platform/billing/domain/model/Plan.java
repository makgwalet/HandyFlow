package za.co.handyflow.platform.billing.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "included_module_count", nullable = false)
    private Integer includedModuleCount;

    /*
     * WHY JSONB for features?
     * Feature flags evolve constantly as the product grows.
     * Adding "max_invoices_per_month" should NOT need a DB migration.
     * JSONB stores it flexibly AND PostgreSQL can index/query inside it.
     */

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> features;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "plan_modules",
            joinColumns = @JoinColumn(name = "plan_id")
    )
    @Column(name = "module_key")
    private Set<String> includedModules =new HashSet<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column
    private String description;

    // ── Business Logic ────────────────────────────────────────────────────────

    public boolean includesAllModules() {
        // -1 means unlimited — Enterprise plan
        return this.includedModuleCount == -1;
    }

    public boolean hasUnlimitedUsers() {
        return this.maxUsers == -1;
    }

    public boolean includesModule(String moduleKey) {
        if (includesAllModules()) return true;
        return includedModules.contains(moduleKey);
    }

    public boolean hasFeature(String featureKey) {
        if (features == null) return false;
        Object value = features.get(featureKey);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return !s.equals("false");
        return value != null;
    }

    public Object getFeatureValue(String featureKey) {
        if (features == null) return null;
        return features.get(featureKey);
    }

    public int priceInRands() {
        return priceCents / 100;
    }
}

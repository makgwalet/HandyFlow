package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable checklist (e.g. "Monthly fire extinguisher check"). Editing replaces the items and bumps
 * {@code revision}; inspections already started keep their own copy of the prompts, so history never changes.
 */
@Entity
@Table(name = "facility_inspection_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityInspectionTemplate {

    public static final int MAX_ITEMS = 100;
    public static final int MAX_PROMPT = 300;

    /** Item as supplied by the caller. */
    public record ItemSpec(String prompt, boolean critical) {}

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Optional hint, e.g. FIRE or HVAC, for the app to suggest this template for matching assets. */
    @Column(name = "asset_category", length = 60)
    private String assetCategory;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int revision = 1;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "template_id", nullable = false)
    @OrderBy("position ASC")
    private List<FacilityInspectionTemplateItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FacilityInspectionTemplate create(TenantId tenantId, String name, String description,
                                                    String assetCategory, List<ItemSpec> specs) {
        FacilityInspectionTemplate t = new FacilityInspectionTemplate();
        t.tenantId = tenantId;
        t.apply(name, description, assetCategory, specs);
        t.createdAt = Instant.now();
        t.updatedAt = Instant.now();
        return t;
    }

    public void update(String name, String description, String assetCategory, List<ItemSpec> specs) {
        apply(name, description, assetCategory, specs);
        this.revision++;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    private void apply(String name, String description, String assetCategory, List<ItemSpec> specs) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A template needs a name");
        if (specs == null || specs.isEmpty()) throw new IllegalArgumentException("A template needs at least one check");
        if (specs.size() > MAX_ITEMS) throw new IllegalArgumentException("A template can have at most " + MAX_ITEMS + " checks");
        for (ItemSpec s : specs) {
            if (s == null || s.prompt() == null || s.prompt().isBlank())
                throw new IllegalArgumentException("Every check needs a description");
            if (s.prompt().trim().length() > MAX_PROMPT)
                throw new IllegalArgumentException("A check can be at most " + MAX_PROMPT + " characters");
        }
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.assetCategory = assetCategory == null || assetCategory.isBlank() ? null : assetCategory.trim().toUpperCase();
        this.items.clear();
        int pos = 1;
        for (ItemSpec s : specs) this.items.add(FacilityInspectionTemplateItem.of(pos++, s.prompt(), s.critical()));
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}

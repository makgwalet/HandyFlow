package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** One planned amount for one category of spend (venue, catering, AV, ...). */
@Entity
@Table(name = "event_budget_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventBudgetLine {

    public static final Set<String> CATEGORIES = Set.of(
            "VENUE", "CATERING", "AV_PRODUCTION", "SECURITY", "TRANSPORT", "ENTERTAINMENT",
            "MARKETING", "STAFFING", "PRINTING_SIGNAGE", "INSURANCE_PERMITS", "DECOR",
            "ACCOMMODATION", "CONTINGENCY", "OTHER");

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)      private UUID tenantId;
    @Column(name = "event_id", nullable = false)       private UUID eventId;
    @Column(name = "category", nullable = false)       private String category;
    @Column(name = "description", nullable = false)    private String description;
    @Column(name = "planned_amount", nullable = false) private BigDecimal plannedAmount;
    @Column(name = "notes")                            private String notes;
    @Column(name = "created_at", nullable = false)     private Instant createdAt;
    @Column(name = "updated_at", nullable = false)     private Instant updatedAt;

    public static EventBudgetLine create(TenantId tenantId, UUID eventId, String category,
                                         String description, BigDecimal plannedAmount, String notes) {
        EventBudgetLine l = new EventBudgetLine();
        l.id        = UUID.randomUUID();
        l.tenantId  = tenantId.getValue();
        l.eventId   = eventId;
        l.createdAt = Instant.now();
        l.apply(category, description, plannedAmount, notes);
        return l;
    }

    public void update(String category, String description, BigDecimal plannedAmount, String notes) {
        apply(category, description, plannedAmount, notes);
    }

    private void apply(String category, String description, BigDecimal plannedAmount, String notes) {
        String normalised = normaliseCategory(category);
        if (!CATEGORIES.contains(normalised))
            throw new IllegalArgumentException("Unknown budget category '" + category + "'. Valid: " + CATEGORIES);
        if (plannedAmount == null || plannedAmount.signum() < 0)
            throw new IllegalArgumentException("Planned amount must be zero or more");
        this.category      = normalised;
        this.description   = description.trim();
        this.plannedAmount = plannedAmount.setScale(2, RoundingMode.HALF_UP);
        this.notes         = notes;
        this.updatedAt     = Instant.now();
    }

    static String normaliseCategory(String category) {
        return category == null ? "" : category.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }
}
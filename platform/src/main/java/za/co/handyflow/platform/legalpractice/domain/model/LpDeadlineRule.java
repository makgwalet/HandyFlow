package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * How one kind of period is counted: its length, its unit (court days, calendar days, months, years), whether it runs
 * forward from or back from the trigger date, how it moves off a non-working last day, and an optional yearly recess
 * window that is not counted. The values are checked by the service (through the calculator) and again by the database.
 * A rule is deactivated, not deleted, because computations refer to it by code and keep their own copy.
 */
@Entity
@Table(name = "lp_deadline_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpDeadlineRule {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(length = 40)
    private String court;

    /** What the trigger date is, in words the person entering it will recognise: "Date summons was served". */
    @Column(name = "trigger_label", nullable = false, length = 120)
    private String triggerLabel;

    /** The kind of key date a computation creates: COURT_DATE | PRESCRIPTION_DEADLINE | FILING_DEADLINE | LIMITATION_PERIOD | OTHER. */
    @Column(name = "key_date_type", nullable = false, length = 30)
    private String keyDateType;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 14)
    private String unit; // COURT_DAYS | CALENDAR_DAYS | MONTHS | YEARS

    @Column(nullable = false, length = 6)
    private String direction; // AFTER | BEFORE

    @Column(nullable = false, length = 26)
    private String rollover; // NONE | SKIP_SUNDAY_AND_HOLIDAYS | SKIP_NON_COURT_DAYS

    /** "MM-DD"; both or neither. */
    @Column(name = "recess_start", length = 5)
    private String recessStart;

    @Column(name = "recess_end", length = 5)
    private String recessEnd;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpDeadlineRule create(TenantId tenantId, String code, String name, String description, String court,
                                        String triggerLabel, String keyDateType, int amount, String unit, String direction,
                                        String rollover, String recessStart, String recessEnd, String sourceReference) {
        LpDeadlineRule r = new LpDeadlineRule();
        r.tenantId = tenantId;
        r.createdAt = Instant.now();
        r.apply(code, name, description, court, triggerLabel, keyDateType, amount, unit, direction, rollover, recessStart, recessEnd, sourceReference);
        return r;
    }

    public void update(String code, String name, String description, String court, String triggerLabel, String keyDateType,
                       int amount, String unit, String direction, String rollover, String recessStart, String recessEnd,
                       String sourceReference) {
        apply(code, name, description, court, triggerLabel, keyDateType, amount, unit, direction, rollover, recessStart, recessEnd, sourceReference);
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    private void apply(String code, String name, String description, String court, String triggerLabel, String keyDateType,
                       int amount, String unit, String direction, String rollover, String recessStart, String recessEnd,
                       String sourceReference) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.court = court;
        this.triggerLabel = triggerLabel;
        this.keyDateType = keyDateType;
        this.amount = amount;
        this.unit = unit;
        this.direction = direction;
        this.rollover = rollover;
        this.recessStart = recessStart;
        this.recessEnd = recessEnd;
        this.sourceReference = sourceReference;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}

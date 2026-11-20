package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The firm's record that a due date was worked out: the rule as it was (a copy, so a later change to the rule does not
 * rewrite history), the trigger date, the result, the days that were skipped and why, and who did it. Never changed or
 * deleted. A correction is a new row that supersedes the old one and must say why; the history is therefore a line.
 */
@Entity
@Table(name = "lp_deadline_computations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpDeadlineComputation {

    public static final int MIN_REASON = 10;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, updatable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false, updatable = false)
    private UUID matterId;

    @Column(name = "key_date_id", nullable = false, updatable = false)
    private UUID keyDateId;

    @Column(name = "supersedes_id", updatable = false)
    private UUID supersedesId;

    @Column(name = "rule_id", updatable = false)
    private UUID ruleId;

    @Column(name = "rule_code", nullable = false, updatable = false, length = 60)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, updatable = false, length = 200)
    private String ruleName;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(nullable = false, updatable = false, length = 14)
    private String unit;

    @Column(nullable = false, updatable = false, length = 6)
    private String direction;

    @Column(nullable = false, updatable = false, length = 26)
    private String rollover;

    @Column(name = "recess_start", updatable = false, length = 5)
    private String recessStart;

    @Column(name = "recess_end", updatable = false, length = 5)
    private String recessEnd;

    @Column(name = "trigger_label", nullable = false, updatable = false, length = 120)
    private String triggerLabel;

    @Column(name = "trigger_date", nullable = false, updatable = false)
    private LocalDate triggerDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    /** One line per skipped day, separated by newlines; empty when nothing was skipped. */
    @Column(nullable = false, updatable = false)
    private String explanation;

    @Column(updatable = false)
    private String reason;

    @Column(name = "computed_by", nullable = false, updatable = false)
    private UUID computedBy;

    @Column(name = "computed_by_name", updatable = false)
    private String computedByName;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    /** A computation from a rule. {@code supersedes} is null for the first one on a key date. */
    public static LpDeadlineComputation of(TenantId tenantId, UUID matterId, UUID keyDateId, UUID supersedes, UUID ruleId,
                                           String ruleCode, String ruleName, int amount, String unit, String direction,
                                           String rollover, String recessStart, String recessEnd, String triggerLabel,
                                           LocalDate triggerDate, LocalDate dueDate, String explanation, String reason,
                                           UUID by, String byName) {
        if (supersedes != null && (reason == null || reason.trim().length() < MIN_REASON)) {
            throw new IllegalArgumentException("Say why the date is being corrected (at least " + MIN_REASON + " characters)");
        }
        LpDeadlineComputation c = new LpDeadlineComputation();
        c.tenantId = tenantId;
        c.matterId = matterId;
        c.keyDateId = keyDateId;
        c.supersedesId = supersedes;
        c.ruleId = ruleId;
        c.ruleCode = ruleCode;
        c.ruleName = ruleName;
        c.amount = amount;
        c.unit = unit;
        c.direction = direction;
        c.rollover = rollover;
        c.recessStart = recessStart;
        c.recessEnd = recessEnd;
        c.triggerLabel = triggerLabel;
        c.triggerDate = triggerDate;
        c.dueDate = dueDate;
        c.explanation = explanation == null ? "" : explanation;
        c.reason = reason == null ? null : reason.trim();
        c.computedBy = by;
        c.computedByName = byName;
        c.computedAt = Instant.now();
        return c;
    }
}

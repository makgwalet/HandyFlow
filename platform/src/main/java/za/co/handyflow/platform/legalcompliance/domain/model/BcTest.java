package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** An exercise of a continuity plan. Append-only. The process's recovery time target at the time is kept on the row so the result stays meaningful if the target changes later. */
@Entity
@Table(name = "legalcompliance_bc_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BcTest extends AggregateRoot<BcTest> {

    public static final int MIN_TEXT = 15;

    @Column(name = "plan_id", nullable = false) private UUID planId;
    @Column(name = "test_date", nullable = false) private LocalDate testDate;
    @Enumerated(EnumType.STRING) @Column(name = "test_type", nullable = false, length = 12) private BcTestType testType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 18) private BcTestOutcome outcome;
    @Column(name = "rto_target_hours", nullable = false) private int rtoTargetHours;
    @Column(name = "achieved_rto_hours", precision = 8, scale = 2) private BigDecimal achievedRtoHours;
    @Column(columnDefinition = "TEXT") private String issues;
    @Column(columnDefinition = "TEXT") private String actions;
    @Column(name = "conducted_by_name", nullable = false, length = 255) private String conductedByName;
    @Column(name = "recorded_by_user_id", nullable = false) private UUID recordedByUserId;
    @Column(name = "recorded_by_name", nullable = false, length = 255) private String recordedByName;

    public static BcTest record(TenantId t, UUID planId, int rtoTarget, LocalDate testDate, BcTestType type, BcTestOutcome outcome, BigDecimal achievedRto, String issues, String actions,
                                String conductedBy, UUID uid, String uname, LocalDate today) {
        if (testDate == null) throw new IllegalArgumentException("The test date is required");
        if (testDate.isAfter(today)) throw new IllegalArgumentException("A test cannot be dated in the future");
        if (type == null) throw new IllegalArgumentException("A test type is required");
        if (outcome == null) throw new IllegalArgumentException("An outcome is required");
        if (conductedBy == null || conductedBy.isBlank()) throw new IllegalArgumentException("Who conducted the test is required");
        if (achievedRto != null && achievedRto.signum() < 0) throw new IllegalArgumentException("The recovery time cannot be negative");
        if (type == BcTestType.FAILOVER && achievedRto == null) throw new IllegalArgumentException("A failover test must record the recovery time achieved");
        if (outcome == BcTestOutcome.PASSED && achievedRto != null && achievedRto.compareTo(BigDecimal.valueOf(rtoTarget)) > 0)
            throw new IllegalArgumentException("A test that missed the recovery time objective cannot be recorded as passed");
        if (outcome != BcTestOutcome.PASSED) {
            if (issues == null || issues.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe what went wrong (at least " + MIN_TEXT + " characters)");
            if (actions == null || actions.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Record the follow-up actions (at least " + MIN_TEXT + " characters)");
        }
        BcTest x = new BcTest();
        x.initTenantId(t);
        x.planId = planId;
        x.testDate = testDate;
        x.testType = type;
        x.outcome = outcome;
        x.rtoTargetHours = rtoTarget;
        x.achievedRtoHours = achievedRto;
        x.issues = issues == null || issues.isBlank() ? null : issues.trim();
        x.actions = actions == null || actions.isBlank() ? null : actions.trim();
        x.conductedByName = conductedBy.trim();
        x.recordedByUserId = uid;
        x.recordedByName = uname == null || uname.isBlank() ? "Unknown" : uname;
        return x;
    }

    /** null when no recovery time was measured. */
    public Boolean rtoMet() { return achievedRtoHours == null ? null : achievedRtoHours.compareTo(BigDecimal.valueOf(rtoTargetHours)) <= 0; }
}

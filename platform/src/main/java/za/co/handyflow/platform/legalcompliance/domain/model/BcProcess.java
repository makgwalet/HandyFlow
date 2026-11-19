package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/** A business process that must keep running (or be restored) within a stated time. RTO = how long it may be down; RPO = how much recent data may be lost, both in hours. */
@Entity
@Table(name = "legalcompliance_bc_processes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BcProcess extends AggregateRoot<BcProcess> {

    public static final int MIN_REASON = 10;

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(length = 100) private String department;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Criticality criticality;
    @Column(name = "rto_hours", nullable = false) private int rtoHours;
    @Column(name = "rpo_hours", nullable = false) private int rpoHours;
    @Column(columnDefinition = "TEXT") private String dependencies;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private BcProcessStatus status = BcProcessStatus.ACTIVE;
    @Column(name = "bia_reviewed_on") private LocalDate biaReviewedOn;
    @Column(name = "bia_review_months", nullable = false) private int biaReviewMonths = 12;
    @Column(name = "retired_reason", columnDefinition = "TEXT") private String retiredReason;

    public static BcProcess create(TenantId t, String reference, String name, String description, String owner, String department, Criticality criticality, int rto, int rpo,
                                   String dependencies, int biaMonths) {
        BcProcess p = new BcProcess();
        p.initTenantId(t);
        p.reference = reference;
        p.apply(name, description, owner, department, criticality, rto, rpo, dependencies, biaMonths);
        return p;
    }

    public void update(String name, String description, String owner, String department, Criticality criticality, int rto, int rpo, String dependencies, int biaMonths) {
        if (status == BcProcessStatus.RETIRED) throw new IllegalStateException("A retired process cannot be edited");
        apply(name, description, owner, department, criticality, rto, rpo, dependencies, biaMonths);
    }

    private void apply(String name, String description, String owner, String department, Criticality criticality, int rto, int rpo, String dependencies, int biaMonths) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A name is required");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (criticality == null) throw new IllegalArgumentException("A criticality is required");
        if (rto < 1 || rto > 8760) throw new IllegalArgumentException("The recovery time objective must be between 1 and 8760 hours");
        if (rpo < 0 || rpo > 8760) throw new IllegalArgumentException("The recovery point objective must be between 0 and 8760 hours");
        if (biaMonths < 1 || biaMonths > 36) throw new IllegalArgumentException("The impact-analysis review period must be between 1 and 36 months");
        this.name = name.trim();
        this.description = blankToNull(description);
        this.ownerName = owner.trim();
        this.department = blankToNull(department);
        this.criticality = criticality;
        this.rtoHours = rto;
        this.rpoHours = rpo;
        this.dependencies = blankToNull(dependencies);
        this.biaReviewMonths = biaMonths;
    }

    public void markBiaReviewed(LocalDate on, LocalDate today) {
        if (status == BcProcessStatus.RETIRED) throw new IllegalStateException("A retired process cannot be reviewed");
        if (on == null) throw new IllegalArgumentException("The review date is required");
        if (on.isAfter(today)) throw new IllegalArgumentException("A review cannot be dated in the future");
        if (biaReviewedOn != null && on.isBefore(biaReviewedOn)) throw new IllegalArgumentException("A review cannot be dated before the previous one");
        this.biaReviewedOn = on;
    }

    public void retire(String reason) {
        if (status == BcProcessStatus.RETIRED) throw new IllegalStateException("This process is already retired");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("A reason of at least " + MIN_REASON + " characters is required");
        this.status = BcProcessStatus.RETIRED;
        this.retiredReason = reason.trim();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}

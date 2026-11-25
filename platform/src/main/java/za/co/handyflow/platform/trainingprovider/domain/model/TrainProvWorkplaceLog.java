package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.WorkplaceRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One dated logbook entry. Hours and what was done. Confirmed once by the mentor (recorded by staff). Voided with a reason, never deleted. */
@Entity(name = "TrainProvWorkplaceLog")
@Table(name = "trainprov_workplace_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvWorkplaceLog {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "placement_id", nullable = false, updatable = false)
    private UUID placementId;
    @Column(name = "log_date", nullable = false, updatable = false)
    private LocalDate logDate;
    @Column(nullable = false, updatable = false)
    private BigDecimal hours;
    @Column(nullable = false, updatable = false)
    private String activity;
    @Column(nullable = false)
    private String status = WorkplaceRules.RECORDED;
    @Column(name = "void_reason")
    private String voidReason;
    @Column(name = "void_by")
    private UUID voidBy;
    @Column(name = "confirmed_on")
    private LocalDate confirmedOn;
    @Column(name = "confirmed_by_name")
    private String confirmedByName;
    @Column(name = "confirmation_by")
    private UUID confirmationBy;
    @Column(name = "entered_by", nullable = false, updatable = false)
    private UUID enteredBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvWorkplaceLog record(TenantId t, UUID placementId, LocalDate day, BigDecimal hours, String activity, UUID by) {
        TrainProvWorkplaceLog l = new TrainProvWorkplaceLog();
        l.tenantId = t.getValue();
        l.placementId = placementId;
        l.logDate = day;
        l.hours = hours;
        l.activity = activity.trim();
        l.enteredBy = by;
        l.createdAt = Instant.now();
        l.updatedAt = l.createdAt;
        return l;
    }

    public boolean isConfirmed() {
        return confirmedOn != null;
    }

    public void confirm(LocalDate on, String mentorName, UUID by, LocalDate today) {
        String problem = WorkplaceRules.confirmProblem(status, isConfirmed(), on, logDate, today, mentorName);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.confirmedOn = on;
        this.confirmedByName = mentorName.trim();
        this.confirmationBy = by;
        this.updatedAt = Instant.now();
    }

    public void voidIt(String reason, UUID by) {
        String problem = WorkplaceRules.voidLogProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = WorkplaceRules.VOID;
        this.voidReason = reason.trim();
        this.voidBy = by;
        this.updatedAt = Instant.now();
    }
}

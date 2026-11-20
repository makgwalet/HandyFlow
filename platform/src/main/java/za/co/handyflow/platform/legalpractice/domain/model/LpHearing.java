package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.LitigationRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A court date. It is scheduled, then held (with what happened) or cancelled (with why). A postponement moves the date and counts. Never deleted. */
@Entity
@Table(name = "lp_hearings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpHearing {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "hearing_type", nullable = false, length = 20) private String hearingType;
    @Column(name = "hearing_date", nullable = false) private LocalDate hearingDate;
    @Column(name = "hearing_time") private LocalTime hearingTime;
    @Column(name = "court_room") private String courtRoom;
    private String appearing;
    @Column(nullable = false, length = 10) private String status = "SCHEDULED";
    @Column(nullable = false) private int postponements;
    private String outcome;
    @Column(name = "cancel_reason") private String cancelReason;
    @Column(name = "key_date_id") private UUID keyDateId;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_by_name", updatable = false) private String createdByName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Version
    private Long version;

    public static LpHearing schedule(TenantId tenantId, UUID caseId, UUID matterId, String type, LocalDate date, LocalTime time, String room, String appearing, UUID by, String byName, Instant now) {
        String problem = LitigationRules.hearingProblem(type, date);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (by == null) throw new IllegalArgumentException("A hearing must say who set it");
        LpHearing h = new LpHearing();
        h.tenantId = tenantId; h.caseId = caseId; h.matterId = matterId; h.hearingType = type; h.hearingDate = date; h.hearingTime = time;
        h.courtRoom = blank(room); h.appearing = blank(appearing); h.createdBy = by; h.createdByName = byName; h.createdAt = now; h.updatedAt = now;
        return h;
    }

    public boolean scheduled() { return "SCHEDULED".equals(status); }

    public void linkKeyDate(UUID keyDateId) { this.keyDateId = keyDateId; }

    private void requireScheduled() { if (!scheduled()) throw new IllegalStateException("This hearing has already been " + status.toLowerCase() + ""); }

    /** A postponement: the hearing moves to a new date. Returns the date it was on. */
    public LocalDate postpone(LocalDate newDate, LocalTime newTime, String reason, Instant now) {
        requireScheduled();
        String problem = LitigationRules.textProblem(reason, "why it was postponed");
        if (problem != null) throw new IllegalArgumentException(problem);
        if (newDate == null) throw new IllegalArgumentException("Enter the new date");
        if (newDate.equals(hearingDate) && java.util.Objects.equals(newTime, hearingTime)) throw new IllegalStateException("That is the date and time it is already set for");
        LocalDate was = hearingDate;
        hearingDate = newDate; hearingTime = newTime; postponements++; updatedAt = now;
        return was;
    }

    public void hold(String outcome, LocalDate today, Instant now) {
        requireScheduled();
        String problem = LitigationRules.heldProblem(hearingDate, outcome, today);
        if (problem != null) throw new IllegalArgumentException(problem);
        status = "HELD"; this.outcome = outcome.trim(); updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        requireScheduled();
        String problem = LitigationRules.textProblem(reason, "why it was cancelled");
        if (problem != null) throw new IllegalArgumentException(problem);
        status = "CANCELLED"; cancelReason = reason.trim(); updatedAt = now;
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}

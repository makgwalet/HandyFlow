package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.LitigationRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What the court ordered, and by when the firm's client has to comply. The order itself is never rewritten; compliance is recorded once. */
@Entity
@Table(name = "lp_court_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpCourtOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "hearing_id", updatable = false) private UUID hearingId;
    @Column(name = "order_date", nullable = false, updatable = false) private LocalDate orderDate;
    @Column(nullable = false, updatable = false) private String summary;
    @Column(name = "compliance_due", updatable = false) private LocalDate complianceDue;
    @Column(name = "complied_on") private LocalDate compliedOn;
    @Column(name = "complied_note") private String compliedNote;
    @Column(name = "key_date_id") private UUID keyDateId;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_by_name", updatable = false) private String createdByName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @Version
    private Long version;

    public static LpCourtOrder record(TenantId tenantId, UUID caseId, UUID matterId, UUID hearingId, LocalDate orderDate, String summary, LocalDate complianceDue,
                                      UUID by, String byName, LocalDate today, Instant now) {
        String problem = LitigationRules.orderProblem(orderDate, summary, complianceDue, today);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (by == null) throw new IllegalArgumentException("An order must say who recorded it");
        LpCourtOrder o = new LpCourtOrder();
        o.tenantId = tenantId; o.caseId = caseId; o.matterId = matterId; o.hearingId = hearingId; o.orderDate = orderDate;
        o.summary = summary.trim(); o.complianceDue = complianceDue; o.createdBy = by; o.createdByName = byName; o.createdAt = now;
        return o;
    }

    public void linkKeyDate(UUID keyDateId) { this.keyDateId = keyDateId; }

    public boolean outstanding() { return complianceDue != null && compliedOn == null; }

    public void comply(LocalDate on, String note, LocalDate today) {
        if (complianceDue == null) throw new IllegalStateException("This order has no date to comply by");
        if (compliedOn != null) throw new IllegalStateException("Compliance with this order has already been recorded");
        String problem = LitigationRules.compliedProblem(orderDate, on, today);
        if (problem != null) throw new IllegalArgumentException(problem);
        compliedOn = on; compliedNote = note == null || note.isBlank() ? null : note.trim();
    }
}

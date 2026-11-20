package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * A day that is not a normal working day for the firm's deadlines but cannot be worked out: a holiday proclaimed for one
 * year only (kind PUBLIC_HOLIDAY: it also counts for the Sunday-and-holiday rollover), or a day the courts were closed
 * (kind COURT_CLOSURE: it counts only for court days and the court-day rollover).
 */
@Entity
@Table(name = "lp_court_calendar_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpCourtCalendarEntry {

    public static final Set<String> KINDS = Set.of("PUBLIC_HOLIDAY", "COURT_CLOSURE");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 14)
    private String kind;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static LpCourtCalendarEntry create(TenantId tenantId, LocalDate date, String kind, String reason, UUID by, String byName) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("The kind must be PUBLIC_HOLIDAY or COURT_CLOSURE");
        if (reason == null || reason.trim().length() < 3) throw new IllegalArgumentException("Give a reason of at least 3 characters");
        LpCourtCalendarEntry e = new LpCourtCalendarEntry();
        e.tenantId = tenantId;
        e.entryDate = date;
        e.kind = kind;
        e.reason = reason.trim();
        e.createdBy = by;
        e.createdByName = byName;
        e.createdAt = Instant.now();
        return e;
    }
}

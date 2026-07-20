package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A client's accounting period. Maps to acc_periods — a table that
 * already existed (V58__accountant_module.sql) with no application-
 * layer code at all, same situation as acc_fica_documents before it.
 * <p>
 * Deliberately no opening-balance field on this entity — there isn't
 * one on the real table. Opening balances for a trial balance are
 * derived by summing all posted journal activity in every period
 * strictly before the requested one (see AccJournalLineRepository.
 * sumByAccountBeforePeriod()), not read from a stored value.
 */
@Entity(name = "AccountantPeriod")
@Table(name = "acc_periods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccPeriod {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id",    nullable = false) private UUID tenantId;
    @Column(name = "client_id",    nullable = false) private UUID clientId;
    @Column(name = "period_year",  nullable = false) private int periodYear;
    @Column(name = "period_month", nullable = false) private int periodMonth;
    @Column(name = "status",       nullable = false) private String status = "OPEN";
    @Column(name = "closed_at")  private Instant closedAt;
    @Column(name = "closed_by")  private UUID closedBy;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    /**
     * NEW: closes a real blocker found while building the Create
     * Journal form — there was no way for any caller to obtain a valid
     * periodId at all, since no period-creation or period-listing
     * endpoint existed anywhere. Periods now materialize on first use
     * via AccountantService.createJournal()'s resolve-or-create logic,
     * matching how a real user actually interacts with this (picking a
     * date, not a period UUID they have no way to look up).
     */
    public static AccPeriod create(UUID tenantId, UUID clientId, int periodYear, int periodMonth) {
        AccPeriod p = new AccPeriod();
        p.tenantId    = tenantId;
        p.clientId    = clientId;
        p.periodYear  = periodYear;
        p.periodMonth = periodMonth;
        p.createdAt   = Instant.now();
        return p;
    }
}
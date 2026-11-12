package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Phase 7c: a supply contract that groups and reports. It holds no prices; it links to the account's existing price entries. */
@Entity
@Table(name = "fuel_contracts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelContract {

    public static final String ACTIVE = "ACTIVE", CANCELLED = "CANCELLED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "contract_number", nullable = false, length = 40)
    private String contractNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "fuel_type", length = 30)
    private String fuelType;

    @Column(name = "committed_litres")
    private BigDecimal committedLitres;

    @Column(length = 2000)
    private String terms;

    @Column(nullable = false, length = 12)
    private String status = ACTIVE;

    @Column(name = "cancelled_reason", length = 300)
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelContract create(TenantId tenantId, UUID accountId, String number, String title, LocalDate start, LocalDate end,
                                      String fuelType, BigDecimal committedLitres, String terms, UUID by) {
        if (accountId == null) throw new IllegalArgumentException("Choose the customer account.");
        if (number == null || number.isBlank()) throw new IllegalArgumentException("Give the contract a number.");
        if (number.trim().length() > 40) throw new IllegalArgumentException("The contract number can be at most 40 characters.");
        FuelContract c = new FuelContract();
        c.tenantId = tenantId; c.accountId = accountId; c.contractNumber = number.trim(); c.createdBy = by;
        c.apply(title, start, end, fuelType, committedLitres, terms);
        return c;
    }

    /** Edits the commercial facts. The account and number stay as they were; a cancelled contract cannot be edited. */
    public void update(String title, LocalDate start, LocalDate end, String fuelType, BigDecimal committedLitres, String terms) {
        if (isCancelled()) throw new IllegalStateException("A cancelled contract cannot be changed.");
        apply(title, start, end, fuelType, committedLitres, terms);
    }

    private void apply(String title, LocalDate start, LocalDate end, String fuelType, BigDecimal committedLitres, String terms) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Give the contract a title.");
        if (title.trim().length() > 200) throw new IllegalArgumentException("The title can be at most 200 characters.");
        if (start == null || end == null) throw new IllegalArgumentException("Give the start and end dates.");
        if (end.isBefore(start)) throw new IllegalArgumentException("The end date is before the start date.");
        if (committedLitres != null && committedLitres.signum() <= 0) throw new IllegalArgumentException("The committed litres must be more than zero, or left empty.");
        if (terms != null && terms.trim().length() > 2000) throw new IllegalArgumentException("The terms can be at most 2000 characters.");
        this.title = title.trim(); this.startDate = start; this.endDate = end;
        this.fuelType = fuelType == null || fuelType.isBlank() ? null : fuelType.trim();
        this.committedLitres = committedLitres;
        this.terms = terms == null || terms.isBlank() ? null : terms.trim();
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (isCancelled()) throw new IllegalStateException("This contract is already cancelled.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for cancelling the contract.");
        this.status = CANCELLED; this.cancelledReason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        this.cancelledAt = Instant.now(); this.updatedAt = this.cancelledAt;
    }

    public boolean isCancelled() { return CANCELLED.equals(status); }

    /** CANCELLED, UPCOMING (not started), EXPIRED (past its end date) or ACTIVE, as at {@code today}. */
    public String statusOn(LocalDate today) {
        if (isCancelled()) return CANCELLED;
        if (today.isBefore(startDate)) return "UPCOMING";
        if (today.isAfter(endDate)) return "EXPIRED";
        return ACTIVE;
    }
}

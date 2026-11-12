package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of a price list. account_id empty means the standard list; set means a contract price for that customer.
 * A price is never edited: end it and add a new one, so earlier orders can always be explained.
 */
@Entity
@Table(name = "fuel_prices")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelPrice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "fuel_type", nullable = false, updatable = false, length = 30)
    private String fuelType;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Column(name = "price_per_litre", nullable = false, updatable = false, precision = 10, scale = 4)
    private BigDecimal pricePerLitre;

    @Column(name = "min_litres", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal minLitres = BigDecimal.ZERO;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(length = 200)
    private String note;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** APPROVED (applies), PENDING_APPROVAL (waiting, does not apply yet) or REJECTED (never applies). */
    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus = "APPROVED";

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "approval_reason", length = 300)
    private String approvalReason;

    /** The live price this one replaced on its start date, if any. History only; never used to price. */
    @Column(name = "previous_price_per_litre", updatable = false, precision = 10, scale = 4)
    private BigDecimal previousPricePerLitre;

    /** Latest known purchase cost per litre when the price was entered, if any. History only. */
    @Column(name = "cost_per_litre_at_entry", updatable = false, precision = 10, scale = 4)
    private BigDecimal costPerLitreAtEntry;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private Long version;

    public static FuelPrice create(TenantId tenantId, String fuelType, UUID accountId, BigDecimal price, BigDecimal minLitres,
                                   LocalDate validFrom, LocalDate validTo, String note, UUID by) {
        if (fuelType == null || fuelType.isBlank()) throw new IllegalArgumentException("Choose the fuel type.");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Price per litre cannot be negative.");
        if (minLitres != null && minLitres.signum() < 0) throw new IllegalArgumentException("The volume break cannot be negative.");
        if (validFrom == null) throw new IllegalArgumentException("Give the date the price starts.");
        if (validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("The end date is before the start date.");
        FuelPrice p = new FuelPrice();
        p.tenantId = tenantId; p.fuelType = fuelType.trim(); p.accountId = accountId; p.pricePerLitre = price;
        p.minLitres = minLitres == null ? BigDecimal.ZERO : minLitres; p.validFrom = validFrom; p.validTo = validTo;
        p.note = note == null || note.isBlank() ? null : note.trim(); p.createdBy = by; p.createdAt = Instant.now();
        return p;
    }

    public boolean isApproved() { return "APPROVED".equals(approvalStatus); }

    public boolean isPending() { return "PENDING_APPROVAL".equals(approvalStatus); }

    /** Records what this price replaced and the cost it was set against. Call once, straight after create. */
    public void recordContext(BigDecimal previousPrice, BigDecimal costPerLitre) {
        this.previousPricePerLitre = previousPrice;
        this.costPerLitreAtEntry = costPerLitre;
    }

    /** Holds the price back until the Approvals engine decides. */
    public void submitForApproval(UUID requestId, String reason) {
        this.approvalStatus = "PENDING_APPROVAL";
        this.approvalRequestId = requestId;
        this.approvalReason = reason == null ? null : reason.length() > 300 ? reason.substring(0, 300) : reason;
    }

    /** @return true if this changed anything; repeats and late events are harmless. */
    public boolean approvalGranted() {
        if (!isPending()) return false;
        this.approvalStatus = "APPROVED"; this.decidedAt = Instant.now();
        return true;
    }

    public boolean approvalRejected() {
        if (!isPending()) return false;
        this.approvalStatus = "REJECTED"; this.decidedAt = Instant.now();
        return true;
    }

    /** Price change against what it replaced, as a percentage; null when nothing was replaced. */
    public BigDecimal changePct() {
        if (previousPricePerLitre == null || previousPricePerLitre.signum() == 0) return null;
        return pricePerLitre.subtract(previousPricePerLitre).multiply(new BigDecimal("100"))
                .divide(previousPricePerLitre, 2, java.math.RoundingMode.HALF_UP);
    }

    /** Margin over the cost known at entry, as a percentage of the price; null when cost or price is unknown/zero. */
    public BigDecimal marginPct() {
        return marginPct(pricePerLitre, costPerLitreAtEntry);
    }

    public static BigDecimal marginPct(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || price.signum() <= 0) return null;
        return price.subtract(cost).multiply(new BigDecimal("100")).divide(price, 2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isLive(LocalDate on) {
        return endedAt == null && isApproved() && !on.isBefore(validFrom) && (validTo == null || !on.isAfter(validTo));
    }

    /** Ends the price on the given day (it still applies on that day). */
    public void end(LocalDate lastDay) {
        if (endedAt != null) throw new IllegalStateException("This price has already been ended.");
        if (isPending()) throw new IllegalStateException("This price is waiting for approval. Wait for the decision before ending it.");
        if (lastDay.isBefore(validFrom)) throw new IllegalArgumentException("A price cannot end before it starts.");
        this.validTo = lastDay; this.endedAt = Instant.now();
    }
}

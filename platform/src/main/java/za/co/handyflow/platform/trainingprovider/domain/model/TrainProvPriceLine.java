package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.PriceRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One fee in a programme price book. Never edited: a changed fee is a new line. Withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvPriceLine")
@Table(name = "trainprov_price_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvPriceLine {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private UUID programmeVersionId;
    @Column(name = "funder_id", updatable = false)
    private UUID funderId;
    @Column(name = "fee_type", nullable = false, updatable = false)
    private String feeType;
    @Column(updatable = false)
    private String description;
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;
    @Column(name = "includes_vat", nullable = false, updatable = false)
    private boolean includesVat;
    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;
    @Column(name = "valid_to", updatable = false)
    private LocalDate validTo;
    @Column(name = "source_note", nullable = false, updatable = false)
    private String sourceNote;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "withdrawn_reason")
    private String withdrawnReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static TrainProvPriceLine create(TenantId t, UUID versionId, UUID funderId, String feeType, String description, BigDecimal amount, Boolean includesVat,
                                            LocalDate from, LocalDate to, String sourceNote, UUID by) {
        String problem = PriceRules.problem(feeType, description, amount, includesVat, from, to, sourceNote);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        TrainProvPriceLine p = new TrainProvPriceLine();
        p.tenantId = t.getValue();
        p.programmeVersionId = versionId;
        p.funderId = funderId;
        p.feeType = feeType;
        p.description = description == null || description.isBlank() ? null : description.trim();
        p.amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        p.includesVat = includesVat;
        p.validFrom = from;
        p.validTo = to;
        p.sourceNote = sourceNote.trim();
        p.createdBy = by;
        p.createdAt = Instant.now();
        return p;
    }

    public void withdraw(String reason, UUID by) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("That fee is already withdrawn");
        }
        String problem = PriceRules.withdrawProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        status = "WITHDRAWN";
        withdrawnReason = reason.trim();
        withdrawnBy = by;
        withdrawnAt = Instant.now();
    }
}

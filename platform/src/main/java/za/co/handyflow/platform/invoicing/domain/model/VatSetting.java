package za.co.handyflow.platform.invoicing.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One change to a company's VAT setting, taking effect on a date. Never edited; a change that has not started can be cancelled. */
@Entity
@Table(name = "invoicing_vat_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VatSetting {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vat_registered", nullable = false)
    private boolean vatRegistered;

    @Column(name = "standard_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal standardRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public static VatSetting of(TenantId tenantId, boolean registered, BigDecimal rate, LocalDate from, String reason, UUID by) {
        VatSetting v = new VatSetting();
        v.tenantId = tenantId;
        v.vatRegistered = registered;
        v.standardRate = rate;
        v.effectiveFrom = from;
        v.reason = reason == null || reason.isBlank() ? null : reason.trim();
        v.createdBy = by;
        return v;
    }

    public void cancel() { this.cancelledAt = Instant.now(); }

    public VatRules.Row toRow() { return new VatRules.Row(id, vatRegistered, standardRate, effectiveFrom); }
}

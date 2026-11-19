package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** A beneficial owner of a company or trust party. Ownership is a percentage (>0, ≤100) or blank where control is by other means (then control_basis is required). */
@Entity
@Table(name = "legalcompliance_fica_beneficial_owners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FicaBeneficialOwner extends AggregateRoot<FicaBeneficialOwner> {

    @Column(name = "party_id", nullable = false) private UUID partyId;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "ownership_percent") private BigDecimal ownershipPercent;
    @Column(name = "control_basis", length = 255) private String controlBasis;
    @Column(nullable = false) private boolean verified;

    public static FicaBeneficialOwner add(TenantId tenantId, UUID partyId, String name, BigDecimal percent, String controlBasis) {
        FicaBeneficialOwner o = new FicaBeneficialOwner();
        o.initTenantId(tenantId);
        o.partyId = partyId;
        o.apply(name, percent, controlBasis);
        return o;
    }

    public void update(String name, BigDecimal percent, String controlBasis) {
        apply(name, percent, controlBasis);
        this.verified = false;
    }

    private void apply(String name, BigDecimal percent, String controlBasis) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("The owner's name is required");
        if (percent != null && (percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0)) throw new IllegalArgumentException("Ownership must be more than 0 and at most 100 percent");
        if (percent == null && (controlBasis == null || controlBasis.isBlank())) throw new IllegalArgumentException("Give an ownership percentage or say how the person exercises control");
        this.ownerName = name.trim();
        this.ownershipPercent = percent;
        this.controlBasis = controlBasis == null || controlBasis.isBlank() ? null : controlBasis.trim();
    }

    public void markVerified() { this.verified = true; }
}

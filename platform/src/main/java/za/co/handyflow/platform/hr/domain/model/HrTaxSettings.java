package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The employer's tax reference numbers and the kind of retirement fund its employees belong to. */
@Entity
@Table(name = "hr_tax_settings")
@Getter
@NoArgsConstructor
public class HrTaxSettings {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "paye_reference")       String payeReference;
    @Column(name = "sdl_reference")        String sdlReference;
    @Column(name = "uif_reference")        String uifReference;
    @Column(name = "retirement_fund_type") String retirementFundType = TaxCertificateRules.PENSION;
    @Column(name = "updated_by")           UUID updatedBy;
    @Column(name = "updated_at")           Instant updatedAt;

    public static HrTaxSettings of(UUID tenantId) {
        HrTaxSettings s = new HrTaxSettings();
        s.tenantId = tenantId;
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(String payeRef, String sdlRef, String uifRef, String fundType, UUID actor) {
        this.payeReference = blank(payeRef);
        this.sdlReference = blank(sdlRef);
        this.uifReference = blank(uifRef);
        this.retirementFundType = fundType;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.replaceAll("\\s", ""); }
}

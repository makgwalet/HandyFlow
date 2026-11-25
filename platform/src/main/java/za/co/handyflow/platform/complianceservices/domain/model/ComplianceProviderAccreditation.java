package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An accreditation a provider says they hold. A claim until a different person checks it. Never deleted; retired with a reason. */
@Entity(name = "ComplianceProviderAccreditation")
@Table(name = "compliance_provider_accreditations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceProviderAccreditation {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(nullable = false)
    private String body;
    @Column(nullable = false)
    private String reference;
    @Column(name = "issued_on")
    private LocalDate issuedOn;
    @Column(name = "expires_on")
    private LocalDate expiresOn;
    @Column(nullable = false)
    private String status = ProviderRules.CLAIMED;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
    @Column(name = "checked_by")
    private UUID checkedBy;
    @Column(name = "checked_at")
    private Instant checkedAt;
    @Column(name = "check_note", columnDefinition = "TEXT")
    private String checkNote;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "retire_note", columnDefinition = "TEXT")
    private String retireNote;
    @Version
    private Long version;

    public static ComplianceProviderAccreditation claim(TenantId tenantId, UUID providerId, String body, String reference, LocalDate issuedOn, LocalDate expiresOn, UUID by) {
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        ProviderRules.checkDates(issuedOn, expiresOn);
        ComplianceProviderAccreditation a = new ComplianceProviderAccreditation();
        a.tenantId = tenantId;
        a.providerId = providerId;
        a.body = ProviderRules.required(body, "Who accredited them", 200);
        a.reference = ProviderRules.required(reference, "The accreditation number", 100);
        a.issuedOn = issuedOn;
        a.expiresOn = expiresOn;
        a.recordedBy = by;
        a.recordedAt = Instant.now();
        return a;
    }

    /** Someone other than the person who entered it says it checks out, or does not, and how they checked. */
    public void check(boolean accepted, UUID by, String note) {
        if (!ProviderRules.CLAIMED.equals(status)) throw new IllegalStateException("This accreditation has already been checked or retired");
        ProviderRules.checkChecker(recordedBy, by);
        this.checkNote = ProviderRules.note(note);
        this.status = accepted ? ProviderRules.VERIFIED : ProviderRules.REJECTED;
        this.checkedBy = by;
        this.checkedAt = Instant.now();
    }

    public void retire(UUID by, String note) {
        if (ProviderRules.RETIRED.equals(status)) throw new IllegalStateException("This accreditation is already retired");
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.retireNote = ProviderRules.note(note);
        this.status = ProviderRules.RETIRED;
        this.retiredBy = by;
        this.retiredAt = Instant.now();
    }

    public boolean isUsable(LocalDate today) { return ProviderRules.usable(status, expiresOn, today); }
}

package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A client contact's agreement to receive SMS reminders, with how it was given. Withdrawn, never deleted; a withdrawal takes effect at once. */
@Entity(name = "ComplianceSmsConsent")
@Table(name = "compliance_sms_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceSmsConsent {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(name = "contact_name", nullable = false)
    private String contactName;
    @Column(nullable = false)
    private String phone;
    @Column(nullable = false)
    private String basis;
    @Column(name = "given_by", nullable = false)
    private UUID givenBy;
    @Column(name = "given_at", nullable = false)
    private Instant givenAt;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "withdrawn_note")
    private String withdrawnNote;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceSmsConsent record(TenantId tenantId, UUID clientId, String contactName, String phone, String basis, UUID by) {
        if (contactName == null || contactName.isBlank()) throw new IllegalArgumentException("Who the number belongs to is needed");
        ComplianceSmsConsent c = new ComplianceSmsConsent();
        c.tenantId = tenantId;
        c.clientId = clientId;
        c.contactName = contactName.trim().length() > 200 ? contactName.trim().substring(0, 200) : contactName.trim();
        c.phone = SmsReminderRules.normalisePhone(phone);
        c.basis = SmsReminderRules.requireNote(basis, "How consent was given");
        c.givenBy = by;
        c.givenAt = Instant.now();
        c.createdAt = c.givenAt;
        return c;
    }

    public boolean isActive() {
        return withdrawnAt == null;
    }

    public void withdraw(UUID by, String note) {
        if (!isActive()) throw new IllegalStateException("Consent has already been withdrawn");
        this.withdrawnNote = SmsReminderRules.requireNote(note, "The reason for withdrawing");
        this.withdrawnBy = by;
        this.withdrawnAt = Instant.now();
    }
}

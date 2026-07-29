package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * FIX: "no POPIA consent tracking" gap — a system handling health records
 * (POPIA's "special personal information" category, carrying a higher bar
 * than ordinary personal information) had no consent-capture mechanism at
 * all on the patient record.
 * <p>
 * Deliberately an append-only event log, not a single mutable
 * "current status" row per patient — being able to answer "what was this
 * patient's consent status on date X" is a normal compliance question a
 * log supports and an overwritten field doesn't. Current status for any
 * consent type is simply its most recent event (see ClinicConsentService).
 * <p>
 * This is a tracking tool, not legal advice — recording events here does
 * not itself constitute POPIA compliance; that depends on the practice's
 * actual consent process (what's said, what's signed, how it's obtained),
 * which this only records.
 */
@Entity
@Table(name = "clinic_patient_consents")
@Getter
@NoArgsConstructor
public class ClinicPatientConsent {

    @Id UUID id;
    @Column(name = "tenant_id")  UUID tenantId;
    @Column(name = "patient_id") UUID patientId;
    /** TREATMENT | MEDICAL_AID_SHARING | THIRD_PARTY_REFERRAL | MARKETING | RESEARCH */
    @Column(name = "consent_type") String consentType;
    /** GRANTED | REVOKED */
    String action;
    /** VERBAL | WRITTEN | ELECTRONIC — optional */
    String method;
    @Column(name = "captured_by_name") String capturedByName;
    String notes;
    @Column(name = "created_at") Instant createdAt;

    public static ClinicPatientConsent record(TenantId tenantId, UUID patientId,
                                              String consentType, String action, String method,
                                              String capturedByName, String notes) {
        ClinicPatientConsent c = new ClinicPatientConsent();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId.getValue();
        c.patientId = patientId;
        c.consentType = consentType;
        c.action = action;
        c.method = method;
        c.capturedByName = capturedByName;
        c.notes = notes;
        c.createdAt = Instant.now();
        return c;
    }
}
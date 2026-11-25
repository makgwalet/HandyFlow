package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A practitioner's appointment to one role on one programme. In force only when verified and within its dates. */
@Entity(name = "TrainProvPractitionerAppointment")
@Table(name = "trainprov_practitioner_appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvPractitionerAppointment {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;
    @Column(nullable = false)
    private String role;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "authority_id")
    private UUID authorityId;
    @Column(name = "credential_ref")
    private String credentialRef;
    @Column(name = "credential_note")
    private String credentialNote;
    @Column(name = "valid_from")
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    @Column(name = "verification_source")
    private String verificationSource;
    @Column(name = "verified_on")
    private LocalDate verifiedOn;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "withdrawn_reason")
    private String withdrawnReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvPractitionerAppointment create(TenantId t, UUID practitionerId, String role, UUID programmeId, UUID authorityId,
                                                          String credentialRef, String credentialNote, LocalDate validFrom, LocalDate validTo, UUID by) {
        String problem = PractitionerRules.appointmentProblem(role, validFrom, validTo);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        TrainProvPractitionerAppointment a = new TrainProvPractitionerAppointment();
        a.tenantId = t.getValue();
        a.practitionerId = practitionerId;
        a.role = role;
        a.programmeId = programmeId;
        a.authorityId = authorityId;
        a.credentialRef = blank(credentialRef);
        a.credentialNote = blank(credentialNote);
        a.validFrom = validFrom;
        a.validTo = validTo;
        a.createdBy = by;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    /** Records that the credential was checked. A new check replaces the last one; the history keeps both. */
    public void verify(String source, LocalDate on, UUID by, LocalDate today) {
        if ("WITHDRAWN".equals(status)) {
            throw new IllegalStateException("A withdrawn appointment cannot be verified");
        }
        String problem = PractitionerRules.verifyProblem(role, credentialRef, source, on, today);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.verificationSource = source.trim();
        this.verifiedOn = on;
        this.verifiedBy = by;
        this.updatedAt = Instant.now();
    }

    /** Changing the credential reference clears the verification: the new reference has not been checked. */
    public void changeCredential(String credentialRef, String credentialNote, UUID authorityId, LocalDate validFrom, LocalDate validTo) {
        if ("WITHDRAWN".equals(status)) {
            throw new IllegalStateException("A withdrawn appointment cannot be changed");
        }
        String problem = PractitionerRules.appointmentProblem(role, validFrom, validTo);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        String newRef = blank(credentialRef);
        if (!java.util.Objects.equals(newRef, this.credentialRef)) {
            this.verificationSource = null;
            this.verifiedOn = null;
            this.verifiedBy = null;
        }
        this.credentialRef = newRef;
        this.credentialNote = blank(credentialNote);
        this.authorityId = authorityId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.updatedAt = Instant.now();
    }

    public void withdraw(String reason, UUID by) {
        if ("WITHDRAWN".equals(status)) {
            throw new IllegalStateException("This appointment is already withdrawn");
        }
        String problem = PractitionerRules.reasonProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.status = "WITHDRAWN";
        this.withdrawnReason = reason.trim();
        this.withdrawnBy = by;
        this.withdrawnAt = Instant.now();
        this.updatedAt = this.withdrawnAt;
    }

    public PractitionerRules.Standing standingOn(LocalDate day) {
        return PractitionerRules.standing(status, verifiedOn, validFrom, validTo, day);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

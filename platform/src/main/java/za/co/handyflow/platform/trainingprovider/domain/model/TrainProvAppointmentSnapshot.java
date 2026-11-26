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

/**
 * A copy of a practitioner and their appointment as they stood when an assessment was recorded or decided. Written once, never changed, so a
 * later correction or withdrawal never rewrites who was authorised on the day.
 */
@Entity(name = "TrainProvAppointmentSnapshot")
@Table(name = "trainprov_appointment_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAppointmentSnapshot {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "subject_type", nullable = false, updatable = false)
    private String subjectType;
    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;
    @Column(nullable = false, updatable = false)
    private String role;
    @Column(name = "practitioner_id", nullable = false, updatable = false)
    private UUID practitionerId;
    @Column(name = "practitioner_no", nullable = false, updatable = false)
    private String practitionerNo;
    @Column(name = "full_name", nullable = false, updatable = false)
    private String fullName;
    @Column(name = "employment_type", nullable = false, updatable = false)
    private String employmentType;
    @Column(name = "has_login", nullable = false, updatable = false)
    private boolean hasLogin;
    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;
    @Column(name = "programme_id", nullable = false, updatable = false)
    private UUID programmeId;
    @Column(name = "authority_id", updatable = false)
    private UUID authorityId;
    @Column(name = "authority_name", updatable = false)
    private String authorityName;
    @Column(name = "credential_ref", updatable = false)
    private String credentialRef;
    @Column(name = "valid_from", updatable = false)
    private LocalDate validFrom;
    @Column(name = "valid_to", updatable = false)
    private LocalDate validTo;
    @Column(name = "verification_source", updatable = false)
    private String verificationSource;
    @Column(name = "verified_on", updatable = false)
    private LocalDate verifiedOn;
    @Column(name = "verified_by", updatable = false)
    private UUID verifiedBy;
    @Column(updatable = false)
    private String standing;
    @Column(name = "standing_on", nullable = false, updatable = false)
    private LocalDate standingOn;
    @Column(nullable = false, updatable = false)
    private boolean backfilled;
    @Column(name = "captured_by", nullable = false, updatable = false)
    private UUID capturedBy;
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    public static TrainProvAppointmentSnapshot of(TenantId t, String subjectType, UUID subjectId, TrainProvPractitioner p, TrainProvPractitionerAppointment a,
                                                  String authorityName, PractitionerRules.Standing standing, LocalDate on, UUID by) {
        TrainProvAppointmentSnapshot s = new TrainProvAppointmentSnapshot();
        s.tenantId = t.getValue();
        s.subjectType = subjectType;
        s.subjectId = subjectId;
        s.role = a.getRole();
        s.practitionerId = p.getId();
        s.practitionerNo = p.getPractitionerNo();
        s.fullName = p.fullName();
        s.employmentType = p.getEmploymentType();
        s.hasLogin = p.getUserId() != null;
        s.appointmentId = a.getId();
        s.programmeId = a.getProgrammeId();
        s.authorityId = a.getAuthorityId();
        s.authorityName = authorityName;
        s.credentialRef = a.getCredentialRef();
        s.validFrom = a.getValidFrom();
        s.validTo = a.getValidTo();
        s.verificationSource = a.getVerificationSource();
        s.verifiedOn = a.getVerifiedOn();
        s.verifiedBy = a.getVerifiedBy();
        s.standing = standing.name();
        s.standingOn = on;
        s.backfilled = false;
        s.capturedBy = by;
        s.capturedAt = Instant.now();
        return s;
    }
}

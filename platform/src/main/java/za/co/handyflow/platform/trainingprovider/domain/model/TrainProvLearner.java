package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A person the provider trains in their own right. Archived, never deleted. */
@Entity(name = "TrainProvLearner")
@Table(name = "trainprov_learners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearner {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "learner_no", nullable = false)
    private String learnerNo;
    @Column(name = "first_names", nullable = false)
    private String firstNames;
    @Column(nullable = false)
    private String surname;
    @Column(name = "id_type", nullable = false)
    private String idType;
    @Column(name = "id_number")
    private String idNumber;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    private String email;
    private String phone;
    private String address;
    private String province;
    @Column(name = "client_id")
    private UUID clientId;
    @Column(name = "delegate_id")
    private UUID delegateId;
    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;
    @Column(name = "consent_on")
    private LocalDate consentOn;
    @Column(name = "consent_note")
    private String consentNote;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "archived_reason")
    private String archivedReason;
    @Column(name = "archived_by")
    private UUID archivedBy;
    @Column(name = "archived_at")
    private Instant archivedAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvLearner create(TenantId t, String learnerNo, String firstNames, String surname, String idType, String idNumber,
                                          LocalDate dob, String email, String phone, String address, String province, UUID clientId,
                                          UUID delegateId, boolean consentGiven, LocalDate consentOn, String consentNote, UUID by, LocalDate today) {
        TrainProvLearner l = new TrainProvLearner();
        l.tenantId = t.getValue();
        l.learnerNo = learnerNo;
        l.createdBy = by;
        l.apply(firstNames, surname, idType, idNumber, dob, email, phone, address, province, clientId, delegateId, consentGiven, consentOn, consentNote, today);
        l.createdAt = Instant.now();
        return l;
    }

    public void update(String firstNames, String surname, String idType, String idNumber, LocalDate dob, String email, String phone,
                       String address, String province, UUID clientId, UUID delegateId, boolean consentGiven, LocalDate consentOn,
                       String consentNote, LocalDate today) {
        if ("ARCHIVED".equals(status)) {
            throw new IllegalStateException("An archived learner cannot be changed. Restore the learner first");
        }
        apply(firstNames, surname, idType, idNumber, dob, email, phone, address, province, clientId, delegateId, consentGiven, consentOn, consentNote, today);
    }

    private void apply(String firstNames, String surname, String idType, String idNumber, LocalDate dob, String email, String phone,
                       String address, String province, UUID clientId, UUID delegateId, boolean consentGiven, LocalDate consentOn,
                       String consentNote, LocalDate today) {
        String p = LearnerRules.namesProblem(firstNames, surname);
        if (p == null) {
            p = LearnerRules.idProblem(idType, idNumber, dob, today);
        }
        if (p == null) {
            p = LearnerRules.contactProblem(email);
        }
        if (p == null) {
            p = LearnerRules.consentProblem(consentGiven, consentOn, today);
        }
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        this.firstNames = firstNames.trim();
        this.surname = surname.trim();
        this.idType = idType;
        this.idNumber = idNumber == null || idNumber.isBlank() ? null : idNumber.trim();
        this.dateOfBirth = dob;
        this.email = blank(email);
        this.phone = blank(phone);
        this.address = blank(address);
        this.province = blank(province);
        this.clientId = clientId;
        this.delegateId = delegateId;
        this.consentGiven = consentGiven;
        this.consentOn = consentGiven ? consentOn : null;
        this.consentNote = blank(consentNote);
        this.updatedAt = Instant.now();
    }

    public void archive(String reason, UUID by) {
        if ("ARCHIVED".equals(status)) {
            throw new IllegalStateException("This learner is already archived");
        }
        if (!LearnerRules.longEnough(reason, LearnerRules.MIN_REASON)) {
            throw new IllegalArgumentException("Say why, in at least " + LearnerRules.MIN_REASON + " characters");
        }
        this.status = "ARCHIVED";
        this.archivedReason = reason.trim();
        this.archivedBy = by;
        this.archivedAt = Instant.now();
        this.updatedAt = this.archivedAt;
    }

    public void restore() {
        if (!"ARCHIVED".equals(status)) {
            throw new IllegalStateException("Only an archived learner can be restored");
        }
        this.status = "ACTIVE";
        this.archivedReason = null;
        this.archivedBy = null;
        this.archivedAt = null;
        this.updatedAt = Instant.now();
    }

    public String fullName() {
        return firstNames + " " + surname;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

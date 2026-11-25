package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;

import java.time.Instant;
import java.util.UUID;

/** A person who facilitates, assesses or moderates. Deactivated, never deleted. What they may do is held in appointments. */
@Entity(name = "TrainProvPractitioner")
@Table(name = "trainprov_practitioners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvPractitioner {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "practitioner_no", nullable = false)
    private String practitionerNo;
    @Column(name = "first_names", nullable = false)
    private String firstNames;
    @Column(nullable = false)
    private String surname;
    private String email;
    private String phone;
    @Column(name = "employment_type", nullable = false)
    private String employmentType;
    @Column(name = "user_id")
    private UUID userId;
    private String notes;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "inactive_reason")
    private String inactiveReason;
    @Column(name = "inactive_by")
    private UUID inactiveBy;
    @Column(name = "inactive_at")
    private Instant inactiveAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvPractitioner create(TenantId t, String no, String first, String surname, String email, String phone,
                                               String employmentType, UUID userId, String notes, UUID by) {
        TrainProvPractitioner p = new TrainProvPractitioner();
        p.tenantId = t.getValue();
        p.practitionerNo = no;
        p.createdBy = by;
        p.apply(first, surname, email, phone, employmentType, userId, notes);
        p.createdAt = Instant.now();
        return p;
    }

    public void update(String first, String surname, String email, String phone, String employmentType, UUID userId, String notes) {
        if ("INACTIVE".equals(status)) {
            throw new IllegalStateException("An inactive person cannot be changed. Reactivate them first");
        }
        apply(first, surname, email, phone, employmentType, userId, notes);
    }

    private void apply(String first, String surname, String email, String phone, String employmentType, UUID userId, String notes) {
        String problem = PractitionerRules.namesProblem(first, surname);
        if (problem == null) {
            problem = PractitionerRules.contactProblem(employmentType, email);
        }
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.firstNames = first.trim();
        this.surname = surname.trim();
        this.email = blank(email);
        this.phone = blank(phone);
        this.employmentType = employmentType;
        this.userId = userId;
        this.notes = blank(notes);
        this.updatedAt = Instant.now();
    }

    public void deactivate(String reason, UUID by) {
        if ("INACTIVE".equals(status)) {
            throw new IllegalStateException("This person is already inactive");
        }
        String problem = PractitionerRules.reasonProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.status = "INACTIVE";
        this.inactiveReason = reason.trim();
        this.inactiveBy = by;
        this.inactiveAt = Instant.now();
        this.updatedAt = this.inactiveAt;
    }

    public void reactivate() {
        if (!"INACTIVE".equals(status)) {
            throw new IllegalStateException("Only an inactive person can be reactivated");
        }
        this.status = "ACTIVE";
        this.inactiveReason = null;
        this.inactiveBy = null;
        this.inactiveAt = null;
        this.updatedAt = Instant.now();
    }

    public String fullName() {
        return firstNames + " " + surname;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

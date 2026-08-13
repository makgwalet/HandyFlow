package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The agency's own candidate pool — a person the agency knows about,
 * independent of any specific requisition. One candidate can be
 * submitted against multiple requisitions over time (see
 * RecAgencyPlacement, which links a candidate to a specific
 * requisition attempt).
 * <p>
 * CV STORAGE: cvStorageKey is an opaque reference into
 * shared.FileStorageService — deliberately NOT base64-encoded directly
 * into this table. The original platform gap analysis flagged exactly
 * that antipattern in recruiter's own CV storage; built correctly here
 * from the start rather than repeating it in a second module.
 */
@Entity
@Table(name = "reca_candidates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyCandidate {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the AGENCY's tenant

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "current_title")
    private String currentTitle;

    @Column(name = "current_employer")
    private String currentEmployer;

    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills; // free-text, comma-separated — a real
    // structured skills taxonomy is future work,
    // not built in this foundation pass

    @Column(name = "source")
    private String source; // REFERRAL | LINKEDIN | JOB_BOARD | DATABASE | OTHER

    @Column(name = "cv_file_name")
    private String cvFileName;

    @Column(name = "cv_storage_key")
    private String cvStorageKey; // opaque FileStorageService reference — see class Javadoc

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | PLACED | DO_NOT_CONTACT

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RecAgencyCandidate create(UUID tenantId, String fullName, String email, String phone,
                                            String currentTitle, String currentEmployer,
                                            String skills, String source) {
        RecAgencyCandidate c = new RecAgencyCandidate();
        c.tenantId = tenantId;
        c.fullName = fullName;
        c.email = email;
        c.phone = phone;
        c.currentTitle = currentTitle;
        c.currentEmployer = currentEmployer;
        c.skills = skills;
        c.source = source;
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void attachCv(String fileName, String storageKey) {
        this.cvFileName = fileName;
        this.cvStorageKey = storageKey;
        this.updatedAt = Instant.now();
    }

    public void update(String fullName, String email, String phone, String currentTitle,
                       String currentEmployer, String skills, String notes) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.currentTitle = currentTitle;
        this.currentEmployer = currentEmployer;
        this.skills = skills;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void markPlaced() {
        this.status = "PLACED";
        this.updatedAt = Instant.now();
    }

    public void markDoNotContact() {
        this.status = "DO_NOT_CONTACT";
        this.updatedAt = Instant.now();
    }
}
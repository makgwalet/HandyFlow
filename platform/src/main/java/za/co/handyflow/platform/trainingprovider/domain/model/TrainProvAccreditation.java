package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AccreditationRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One accreditation or approval held, or being sought, from one authority.
 * A submitted application is not an approval: only APPROVED and
 * CONDITIONAL_APPROVAL (recorded with the authority's number, a date and
 * where it was verified) can support delivery.
 */
@Entity(name = "TrainProvAccreditation")
@Table(name = "trainprov_accreditations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAccreditation {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "authority_id", nullable = false)
    private UUID authorityId;
    @Column(nullable = false)
    private String title;
    @Column(name = "accreditation_number")
    private String accreditationNumber;
    @Column(nullable = false)
    private String status = AccreditationRules.DRAFT;
    @Column(name = "submitted_on")
    private LocalDate submittedOn;
    @Column(name = "approval_date")
    private LocalDate approvalDate;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    private String conditions;
    @Column(name = "status_reason")
    private String statusReason;
    @Column(name = "verification_source")
    private String verificationSource;
    @Column(name = "last_verified_on")
    private LocalDate lastVerifiedOn;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAccreditation create(TenantId tenantId, UUID authorityId, String title, String number, UUID by) {
        TrainProvAccreditation a = new TrainProvAccreditation();
        a.tenantId = tenantId.getValue();
        a.authorityId = authorityId;
        a.createdBy = by;
        a.setTitleAndNumber(title, number);
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    /** Only while the application is still being prepared or decided. After an approval is recorded the change goes through a status change, which keeps history. */
    public void editDetails(String title, String number, LocalDate expiry, String conditions) {
        if (!AccreditationRules.detailsEditable(status)) {
            throw new IllegalStateException("Details cannot be edited once an accreditation is "
                    + status.toLowerCase().replace('_', ' ') + ". Record a status change instead");
        }
        setTitleAndNumber(title, number);
        this.expiryDate = expiry;
        this.conditions = blankToNull(conditions);
        this.updatedAt = Instant.now();
    }

    /** Applies a status change the rules already allowed. */
    public void applyTransition(String to, String reason, String number, LocalDate approvalDate, LocalDate expiry,
                                String conditions, String verificationSource, LocalDate verifiedOn, UUID by, LocalDate today) {
        if (AccreditationRules.SUBMITTED.equals(to)) {
            this.submittedOn = today;
        }
        if (number != null && !number.isBlank()) {
            this.accreditationNumber = number.trim();
        }
        if (AccreditationRules.isApprovalState(to)) {
            this.approvalDate = approvalDate;
            this.expiryDate = expiry;
            this.conditions = AccreditationRules.CONDITIONAL_APPROVAL.equals(to) ? conditions.trim() : blankToNull(conditions);
            this.verificationSource = verificationSource.trim();
            this.lastVerifiedOn = verifiedOn == null ? today : verifiedOn;
            this.verifiedBy = by;
        }
        this.statusReason = blankToNull(reason);
        this.status = to;
        this.updatedAt = Instant.now();
    }

    public void verify(String source, LocalDate on, UUID by) {
        if (AccreditationRules.isTerminal(status)) {
            throw new IllegalStateException("This accreditation is closed");
        }
        if (source == null || source.trim().length() < AccreditationRules.MIN_SOURCE) {
            throw new IllegalArgumentException("Say where it was verified, for example the authority's letter or register");
        }
        this.verificationSource = source.trim();
        this.lastVerifiedOn = on;
        this.verifiedBy = by;
        this.updatedAt = Instant.now();
    }

    private void setTitleAndNumber(String title, String number) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Give the accreditation a title");
        }
        this.title = title.trim();
        this.accreditationNumber = blankToNull(number);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry in the tenant's POPIA processing-activity register — the
 * org-wide extension of crm.CustomerConsent's per-customer consent
 * record. Where CustomerConsent answers "did THIS customer consent to
 * THIS purpose," a PopiaProcessingActivity answers the register-level
 * question POPIA actually requires an accountable party to be able to
 * answer: "what categories of personal information does this business
 * process, for what purpose, on what lawful basis, across every category
 * of data subject (customers, employees, suppliers, marketing
 * contacts) — not just customers."
 */
@Entity
@Table(name = "legalcompliance_popia_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopiaProcessingActivity extends AggregateRoot<PopiaProcessingActivity> {

    @Column(name = "activity_name", nullable = false, length = 255)
    private String activityName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_category", nullable = false, length = 20)
    private DataCategory dataCategory;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "lawful_basis", nullable = false, length = 30)
    private LawfulBasis lawfulBasis;

    @Column(name = "responsible_department", length = 150)
    private String responsibleDepartment;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @Column(name = "responsible_user_name", length = 255)
    private String responsibleUserName;

    /** Free text, e.g. "7 years — Companies Act 71 of 2008 s24" — deliberately not a fixed period-in-months field, since the governing period is what actually matters for audit, and phrasing it as a duration alone loses that. */
    @Column(name = "retention_period_description", length = 500)
    private String retentionPeriodDescription;

    @Column(name = "cross_border_transfer", nullable = false)
    private boolean crossBorderTransfer;

    @Column(name = "cross_border_details", length = 500)
    private String crossBorderDetails;

    @Column(name = "security_measures", columnDefinition = "TEXT")
    private String securityMeasures;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static PopiaProcessingActivity create(TenantId tenantId, String activityName, DataCategory dataCategory,
                                                  String purpose, LawfulBasis lawfulBasis,
                                                  String responsibleDepartment, UUID responsibleUserId,
                                                  String responsibleUserName, String retentionPeriodDescription,
                                                  boolean crossBorderTransfer, String crossBorderDetails,
                                                  String securityMeasures, LocalDate reviewDate, UUID createdBy) {
        if (crossBorderTransfer && (crossBorderDetails == null || crossBorderDetails.isBlank())) {
            throw new IllegalArgumentException(
                    "crossBorderDetails is required when crossBorderTransfer is true — POPIA s72 requires recording " +
                    "the destination country and the safeguard relied on for any cross-border transfer");
        }
        PopiaProcessingActivity a = new PopiaProcessingActivity();
        a.initTenantId(tenantId);
        a.activityName = activityName;
        a.dataCategory = dataCategory;
        a.purpose = purpose;
        a.lawfulBasis = lawfulBasis;
        a.responsibleDepartment = responsibleDepartment;
        a.responsibleUserId = responsibleUserId;
        a.responsibleUserName = responsibleUserName;
        a.retentionPeriodDescription = retentionPeriodDescription;
        a.crossBorderTransfer = crossBorderTransfer;
        a.crossBorderDetails = crossBorderDetails;
        a.securityMeasures = securityMeasures;
        a.reviewDate = reviewDate;
        a.active = true;
        a.createdBy = createdBy;
        return a;
    }

    public void update(String activityName, DataCategory dataCategory, String purpose, LawfulBasis lawfulBasis,
                       String responsibleDepartment, UUID responsibleUserId, String responsibleUserName,
                       String retentionPeriodDescription, boolean crossBorderTransfer, String crossBorderDetails,
                       String securityMeasures, LocalDate reviewDate) {
        if (crossBorderTransfer && (crossBorderDetails == null || crossBorderDetails.isBlank())) {
            throw new IllegalArgumentException("crossBorderDetails is required when crossBorderTransfer is true");
        }
        this.activityName = activityName;
        this.dataCategory = dataCategory;
        this.purpose = purpose;
        this.lawfulBasis = lawfulBasis;
        this.responsibleDepartment = responsibleDepartment;
        this.responsibleUserId = responsibleUserId;
        this.responsibleUserName = responsibleUserName;
        this.retentionPeriodDescription = retentionPeriodDescription;
        this.crossBorderTransfer = crossBorderTransfer;
        this.crossBorderDetails = crossBorderDetails;
        this.securityMeasures = securityMeasures;
        this.reviewDate = reviewDate;
    }

    public void deactivate() { this.active = false; }
    public void reactivate() { this.active = true; }

    public boolean isDeleted() { return deletedAt != null; }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }
}

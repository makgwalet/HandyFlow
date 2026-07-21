package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rec_applications")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecApplication {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "job_id",       nullable = false) private UUID   jobId;
    @Column(name = "applicant_id", nullable = false) private UUID   applicantId;
    @Column(nullable = false)                         private String stage  = "APPLIED";
    private String  source = "CAREERS_PAGE";
    private Integer score;
    private String  notes;
    @Column(name = "rejection_reason")  private String  rejectionReason;
    @Column(name = "hr_employee_id")    private UUID    hrEmployeeId;
    @Column(name = "applied_at")        private Instant appliedAt;
    @Column(name = "stage_changed_at")  private Instant stageChangedAt;
    @Column(name = "hired_at")          private Instant hiredAt;
    @Column(name = "created_at")        private Instant createdAt;
    @Column(name = "updated_at")        private Instant updatedAt;

    // Offer terms — populated when moved to OFFER stage (see recordOfferTerms).
    // Null until then; null offeredSalary is how the service layer detects
    // "no offer terms recorded yet" before allowing an offer letter to be
    // generated.
    @Column(name = "offered_salary", precision = 12, scale = 2)
    private BigDecimal offeredSalary;
    @Column(name = "offered_salary_frequency") private String    offeredSalaryFrequency;
    @Column(name = "offered_start_date")       private LocalDate offeredStartDate;
    @Column(name = "offer_benefits")           private String    offerBenefits;
    @Column(name = "offer_letter_sent_at")     private Instant   offerLetterSentAt;

    // Referral tracking. referrerName is candidate-supplied free text at
    // apply time (a public applicant has no way to know internal user
    // IDs) — unverified, not on its own enough to trigger a payout.
    // referredByUserId is only set once staff confirms/links the referral
    // to a real employee record; bonus fields are staff-managed from
    // there.
    @Column(name = "referrer_name")            private String     referrerName;
    @Column(name = "referred_by_user_id")      private UUID       referredByUserId;
    @Column(name = "referral_bonus_amount", precision = 12, scale = 2)
    private BigDecimal referralBonusAmount;
    @Column(name = "referral_bonus_status")    private String     referralBonusStatus = "NOT_SET";
    @Column(name = "referral_bonus_paid_at")   private Instant    referralBonusPaidAt;

    public static RecApplication create(TenantId tenantId, UUID jobId,
                                        UUID applicantId, String source, String referrerName) {
        RecApplication a  = new RecApplication();
        a.tenantId        = tenantId;
        a.jobId           = jobId;
        a.applicantId     = applicantId;
        a.source          = source != null ? source : "CAREERS_PAGE";
        a.referrerName    = referrerName;
        a.stage           = "APPLIED";
        a.appliedAt       = Instant.now();
        a.stageChangedAt  = Instant.now();
        a.createdAt       = Instant.now();
        a.updatedAt       = Instant.now();
        return a;
    }

    // ── Pipeline progression ──────────────────────────────────────────────────

    public void moveToStage(String newStage) {
        this.stage          = newStage;
        this.stageChangedAt = Instant.now();
        if ("HIRED".equals(newStage)) {
            this.hiredAt = Instant.now();
            // Minimal automation: flag the bonus as owed once the referred
            // candidate is actually hired. Deliberately doesn't assume
            // anything about probation periods or payout timing — staff
            // still drives PENDING -> APPROVED -> PAID from here.
            if (referredByUserId != null && "NOT_SET".equals(referralBonusStatus)) {
                this.referralBonusStatus = "PENDING";
            }
        }
        touch();
    }

    public void reject(String reason) {
        this.stage           = "REJECTED";
        this.rejectionReason = reason;
        this.stageChangedAt  = Instant.now();
        touch();
    }

    public void withdraw() {
        this.stage          = "WITHDRAWN";
        this.stageChangedAt = Instant.now();
        touch();
    }

    public void linkToEmployee(UUID hrEmployeeId) {
        this.hrEmployeeId = hrEmployeeId;
        touch();
    }

    public void updateNotes(String notes) { this.notes = notes; touch(); }
    public void updateScore(int score)    { this.score = score; touch(); }

    public void recordOfferTerms(BigDecimal offeredSalary, String offeredSalaryFrequency,
                                 LocalDate offeredStartDate, String offerBenefits) {
        this.offeredSalary          = offeredSalary;
        this.offeredSalaryFrequency = offeredSalaryFrequency;
        this.offeredStartDate       = offeredStartDate;
        this.offerBenefits          = offerBenefits;
        touch();
    }

    public void markOfferLetterSent() {
        this.offerLetterSentAt = Instant.now();
        touch();
    }

    public void linkReferral(UUID referredByUserId, BigDecimal referralBonusAmount) {
        if (referredByUserId != null) this.referredByUserId = referredByUserId;
        if (referralBonusAmount != null) this.referralBonusAmount = referralBonusAmount;
        if (this.referredByUserId != null && "NOT_SET".equals(this.referralBonusStatus)) {
            this.referralBonusStatus = "HIRED".equals(this.stage) ? "PENDING" : "NOT_SET";
        }
        touch();
    }

    public void updateReferralBonusStatus(String status) {
        this.referralBonusStatus = status;
        this.referralBonusPaidAt = "PAID".equals(status) ? Instant.now() : this.referralBonusPaidAt;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isActive() {
        return !"REJECTED".equals(stage) && !"WITHDRAWN".equals(stage) && !"HIRED".equals(stage);
    }
}
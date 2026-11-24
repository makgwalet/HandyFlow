package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One numbered version of an obligation. It is a draft until someone else approves it. After that it is
 * never edited: a change is a new version, and an old assessment keeps pointing at the version it used.
 */
@Entity
@Table(name = "compliance_obligation_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceObligationVersion {

    public static final String DRAFT = "DRAFT";
    public static final String IN_REVIEW = "IN_REVIEW";
    public static final String APPROVED = "APPROVED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus = DRAFT;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "legal_source")
    private String legalSource;

    private String regulator;

    @Column(nullable = false)
    private String jurisdiction = "ZA";

    private String frequency;

    @Column(name = "due_rule_type", nullable = false)
    private String dueRuleType = "NONE";

    @Column(name = "due_month")
    private Integer dueMonth;

    @Column(name = "due_day")
    private Integer dueDay;

    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @Column(name = "due_offset_months")
    private Integer dueOffsetMonths;

    @Column(name = "validity_months")
    private Integer validityMonths;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "responsible_role")
    private String responsibleRole;

    @Column(columnDefinition = "TEXT")
    private String guidance;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "authored_by")
    private UUID authoredBy;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "review_exception_reason", columnDefinition = "TEXT")
    private String reviewExceptionReason;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    private Long version;

    /** The text of a version. Its conditions and evidence are checked with {@link ObligationRules} and kept beside it. */
    public record Content(String name, String description, String legalSource, String regulator, String jurisdiction,
                          String frequency, String dueRuleType, Integer dueMonth, Integer dueDay, Integer dueOffsetDays,
                          Integer dueOffsetMonths, Integer validityMonths, String riskLevel, String responsibleRole,
                          String guidance, String changeSummary) {}

    public static ComplianceObligationVersion draft(TenantId tenantId, UUID obligationId, int number, UUID author) {
        ComplianceObligationVersion v = new ComplianceObligationVersion();
        v.tenantId = tenantId;
        v.obligationId = obligationId;
        v.versionNumber = number;
        v.authoredBy = author;
        v.createdAt = Instant.now();
        v.updatedAt = Instant.now();
        return v;
    }

    public boolean isDraft() { return DRAFT.equals(reviewStatus); }
    public boolean isApproved() { return APPROVED.equals(reviewStatus); }

    public void edit(Content c, UUID by) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be edited. Start a new version to change an approved one.");
        String due = c.dueRuleType() == null ? "NONE" : c.dueRuleType().trim().toUpperCase();
        ObligationRules.dueRule(due, c.dueMonth(), c.dueDay(), c.dueOffsetDays(), c.dueOffsetMonths());
        String freq = ClientProfileRules.requireOneOf("frequency", c.frequency(), ObligationRules.FREQUENCIES);
        String risk = ClientProfileRules.requireOneOf("riskLevel", c.riskLevel(), ObligationRules.RISK_LEVELS);
        String role = ClientProfileRules.requireOneOf("responsibleRole", c.responsibleRole(), ObligationRules.RESPONSIBLE_ROLES);
        if (c.validityMonths() != null && (c.validityMonths() < 1 || c.validityMonths() > 600)) throw new IllegalArgumentException("validityMonths must be 1 to 600");
        this.name = ClientProfileRules.clean(c.name(), 255);
        this.description = ClientProfileRules.clean(c.description(), 4000);
        this.legalSource = ClientProfileRules.clean(c.legalSource(), 500);
        this.regulator = ClientProfileRules.clean(c.regulator(), 200);
        this.jurisdiction = c.jurisdiction() == null || c.jurisdiction().isBlank() ? "ZA" : ClientProfileRules.clean(c.jurisdiction().toUpperCase(), 40);
        this.frequency = freq;
        this.dueRuleType = due;
        this.dueMonth = c.dueMonth();
        this.dueDay = c.dueDay();
        this.dueOffsetDays = c.dueOffsetDays();
        this.dueOffsetMonths = c.dueOffsetMonths();
        this.validityMonths = c.validityMonths();
        this.riskLevel = risk;
        this.responsibleRole = role;
        this.guidance = ClientProfileRules.clean(c.guidance(), 4000);
        this.changeSummary = ClientProfileRules.clean(c.changeSummary(), 2000);
        this.updatedAt = Instant.now();
    }

    /** Copies another version's text into this fresh draft. */
    public void copyFrom(ComplianceObligationVersion o, UUID by) {
        edit(new Content(o.name, o.description, o.legalSource, o.regulator, o.jurisdiction, o.frequency, o.dueRuleType, o.dueMonth,
                o.dueDay, o.dueOffsetDays, o.dueOffsetMonths, o.validityMonths, o.riskLevel, o.responsibleRole, o.guidance, null), by);
    }

    /** What a version must say before anyone is asked to review it. */
    public List<String> missingForReview() {
        List<String> m = new java.util.ArrayList<>();
        if (name == null) m.add("name");
        if (legalSource == null) m.add("legal source");
        if (frequency == null) m.add("frequency");
        if (riskLevel == null) m.add("risk level");
        if (responsibleRole == null) m.add("responsible role");
        if (versionNumber > 1 && changeSummary == null) m.add("what changed");
        return m;
    }

    public void submit(UUID by) {
        if (!isDraft()) throw new IllegalStateException("Only a draft can be sent for review");
        List<String> missing = missingForReview();
        if (!missing.isEmpty()) throw new IllegalArgumentException("Before review it needs: " + String.join(", ", missing));
        this.reviewStatus = IN_REVIEW;
        this.submittedBy = by;
        this.submittedAt = Instant.now();
        this.reviewNote = null;
        this.updatedAt = Instant.now();
    }

    /** The reviewer is not the person who wrote or sent it, unless they record why no one else can. */
    public void approve(UUID reviewer, LocalDate from, String exceptionReason, String note) {
        if (!IN_REVIEW.equals(reviewStatus)) throw new IllegalStateException("Only a version in review can be approved");
        if (reviewer == null) throw new IllegalArgumentException("A reviewer is required");
        if (from == null) throw new IllegalArgumentException("Say the date this version takes effect");
        String reason = null;
        if (reviewer.equals(authoredBy) || reviewer.equals(submittedBy)) {
            String r = exceptionReason == null ? "" : exceptionReason.trim();
            if (r.length() < ObligationRules.MIN_EXCEPTION_REASON) {
                throw new IllegalArgumentException("You wrote or sent this, so someone else should approve it. If no one else can, give a reason of at least "
                        + ObligationRules.MIN_EXCEPTION_REASON + " characters");
            }
            reason = r.length() > 1000 ? r.substring(0, 1000) : r;
        }
        this.reviewStatus = APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = ClientProfileRules.clean(note, 2000);
        this.reviewExceptionReason = reason;
        this.effectiveFrom = from;
        this.updatedAt = Instant.now();
    }

    public void reject(UUID reviewer, String reason) {
        if (!IN_REVIEW.equals(reviewStatus)) throw new IllegalStateException("Only a version in review can be sent back");
        String r = reason == null ? "" : reason.trim();
        if (r.length() < ObligationRules.MIN_REVIEW_REASON) throw new IllegalArgumentException("Say what needs to change (at least " + ObligationRules.MIN_REVIEW_REASON + " characters)");
        this.reviewStatus = DRAFT;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = r.length() > 2000 ? r.substring(0, 2000) : r;
        this.updatedAt = Instant.now();
    }

    /** Ends an approved version. Its text is untouched. */
    public void endOn(LocalDate date) {
        if (!isApproved()) throw new IllegalStateException("Only an approved version can end");
        if (effectiveFrom != null && date.isBefore(effectiveFrom)) throw new IllegalArgumentException("It cannot end before it starts");
        this.effectiveTo = date;
        this.updatedAt = Instant.now();
    }

    public ObligationRules.Span span() { return new ObligationRules.Span(versionNumber, isApproved(), effectiveFrom, effectiveTo); }
}

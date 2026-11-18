package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * A deterministic check on a contract, written and approved by the tenant's legal reviewer. The review is evidence that it took
 * place on the date given; it is not a guarantee that the rule is complete or correct for every contract.
 */
@Entity
@Table(name = "contract_risk_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractRiskRule {

    /** Types that need {@code paramText}. */
    public static final Set<String> TEXT_TYPES = Set.of("REQUIRES_CLAUSE_CATEGORY", "REQUIRES_PHRASE", "FORBIDS_PHRASE");
    /** Types that need {@code paramNumber}. */
    public static final Set<String> NUMBER_TYPES = Set.of("VALUE_ABOVE", "TERM_LONGER_THAN_MONTHS", "AUTO_RENEW_NOTICE_BELOW_DAYS");
    /** Types with no parameter. */
    public static final Set<String> PLAIN_TYPES = Set.of("REQUIRES_END_DATE", "REQUIRES_VALUE");
    public static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "family_id") private UUID familyId;
    @Column(name = "version_no") private int versionNo;
    private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "rule_type") private String ruleType;
    @Column(name = "param_text") private String paramText;
    @Column(name = "param_number") private BigDecimal paramNumber;
    @Column(name = "applies_to_types") private String appliesToTypes;
    private String severity;
    @Column(name = "blocks_sending") private boolean blocksSending;
    private String message;
    private String remediation;
    @Column(name = "legal_area") private String legalArea;
    private String jurisdiction;
    private String status;
    @Column(name = "reviewer_name") private String reviewerName;
    @Column(name = "reviewer_professional_details") private String reviewerProfessionalDetails;
    @Column(name = "review_date") private LocalDate reviewDate;
    @Column(name = "next_review_date") private LocalDate nextReviewDate;
    @Column(name = "review_notes", columnDefinition = "TEXT") private String reviewNotes;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Column(name = "withdrawn_reason") private String withdrawnReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static ContractRiskRule create(UUID tenantId, String name, String description, String ruleType, String paramText, BigDecimal paramNumber,
                                          String appliesToTypes, String severity, boolean blocksSending, String message, String remediation,
                                          String legalArea, String jurisdiction, UUID by) {
        ContractRiskRule r = new ContractRiskRule();
        r.id = UUID.randomUUID();
        r.familyId = r.id;
        r.tenantId = tenantId;
        r.versionNo = 1;
        r.status = "DRAFT";
        r.createdBy = by;
        r.createdAt = Instant.now();
        r.apply(name, description, ruleType, paramText, paramNumber, appliesToTypes, severity, blocksSending, message, remediation, legalArea, jurisdiction);
        return r;
    }

    public void edit(String name, String description, String ruleType, String paramText, BigDecimal paramNumber, String appliesToTypes,
                     String severity, boolean blocksSending, String message, String remediation, String legalArea, String jurisdiction) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft rule can be edited (this one is " + status + "). Make a new version instead.");
        apply(name, description, ruleType, paramText, paramNumber, appliesToTypes, severity, blocksSending, message, remediation, legalArea, jurisdiction);
        reviewerName = null; reviewerProfessionalDetails = null; reviewDate = null; nextReviewDate = null; reviewNotes = null;
    }

    private void apply(String name, String description, String ruleType, String paramText, BigDecimal paramNumber, String appliesToTypes,
                       String severity, boolean blocksSending, String message, String remediation, String legalArea, String jurisdiction) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A rule needs a name");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("A rule needs the message shown when it fails");
        String type = ruleType == null ? "" : ruleType.trim().toUpperCase();
        if (!TEXT_TYPES.contains(type) && !NUMBER_TYPES.contains(type) && !PLAIN_TYPES.contains(type)) throw new IllegalArgumentException("Unknown rule type: " + ruleType);
        if (TEXT_TYPES.contains(type) && (paramText == null || paramText.isBlank())) throw new IllegalArgumentException("This rule type needs a value to look for");
        if (NUMBER_TYPES.contains(type) && (paramNumber == null || paramNumber.signum() < 0)) throw new IllegalArgumentException("This rule type needs a number of zero or more");
        if ("REQUIRES_CLAUSE_CATEGORY".equals(type) && !ContractClause.CATEGORIES.contains(paramText.trim().toUpperCase()))
            throw new IllegalArgumentException("Unknown clause category: " + paramText);
        String sev = severity == null ? "" : severity.trim().toUpperCase();
        if (!SEVERITIES.contains(sev)) throw new IllegalArgumentException("Severity must be LOW, MEDIUM, HIGH or CRITICAL");
        this.name = name.trim();
        this.description = description;
        this.ruleType = type;
        this.paramText = TEXT_TYPES.contains(type) ? ("REQUIRES_CLAUSE_CATEGORY".equals(type) ? paramText.trim().toUpperCase() : paramText.trim()) : null;
        this.paramNumber = NUMBER_TYPES.contains(type) ? paramNumber : null;
        this.appliesToTypes = appliesToTypes == null || appliesToTypes.isBlank() ? null
                : Arrays.stream(appliesToTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase).distinct().reduce((a, b) -> a + "," + b).orElse(null);
        this.severity = sev;
        this.blocksSending = blocksSending;
        this.message = message.trim();
        this.remediation = remediation;
        this.legalArea = legalArea;
        this.jurisdiction = jurisdiction == null || jurisdiction.isBlank() ? "South Africa" : jurisdiction;
        this.updatedAt = Instant.now();
    }

    public void approve(String reviewerName, String professionalDetails, LocalDate reviewDate, LocalDate nextReviewDate, String notes, LocalDate today) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft rule can be approved (this one is " + status + ")");
        if (reviewerName == null || reviewerName.isBlank() || professionalDetails == null || professionalDetails.isBlank())
            throw new IllegalArgumentException("The reviewer's name and professional details are required");
        if (reviewDate == null || reviewDate.isAfter(today)) throw new IllegalArgumentException("The review date is required and cannot be in the future");
        if (nextReviewDate != null && !nextReviewDate.isAfter(reviewDate)) throw new IllegalArgumentException("The next review date must be after the review date");
        this.reviewerName = reviewerName.trim();
        this.reviewerProfessionalDetails = professionalDetails.trim();
        this.reviewDate = reviewDate;
        this.nextReviewDate = nextReviewDate;
        this.reviewNotes = notes;
        this.status = "APPROVED";
        this.updatedAt = Instant.now();
    }

    public void publish(LocalDate today) {
        if (!"APPROVED".equals(status)) throw new IllegalStateException("Only an approved rule can be published (this one is " + status + ")");
        if (reviewExpired(today)) throw new IllegalStateException("The legal review of this rule has expired. It needs a new review.");
        status = "PUBLISHED";
        publishedAt = Instant.now();
        updatedAt = publishedAt;
    }

    public void supersede() {
        if (!"PUBLISHED".equals(status)) throw new IllegalStateException("Only a published rule can be superseded");
        status = "SUPERSEDED";
        updatedAt = Instant.now();
    }

    public void withdraw(String reason) {
        if (!"PUBLISHED".equals(status) && !"APPROVED".equals(status)) throw new IllegalStateException("Only an approved or published rule can be withdrawn (this one is " + status + ")");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the rule is being withdrawn");
        status = "WITHDRAWN";
        withdrawnAt = Instant.now();
        withdrawnReason = reason;
        updatedAt = withdrawnAt;
    }

    public ContractRiskRule newVersion(int nextVersionNo, UUID by) {
        if (!"PUBLISHED".equals(status)) throw new IllegalStateException("A new version can only be made from a published rule (this one is " + status + ")");
        ContractRiskRule r = new ContractRiskRule();
        r.id = UUID.randomUUID();
        r.familyId = familyId;
        r.tenantId = tenantId;
        r.versionNo = nextVersionNo;
        r.status = "DRAFT";
        r.createdBy = by;
        r.createdAt = Instant.now();
        r.apply(name, description, ruleType, paramText, paramNumber, appliesToTypes, severity, blocksSending, message, remediation, legalArea, jurisdiction);
        return r;
    }

    public boolean reviewExpired(LocalDate today) { return nextReviewDate != null && today.isAfter(nextReviewDate); }

    /** Published and its review has not lapsed. */
    public boolean active(LocalDate today) { return "PUBLISHED".equals(status) && !reviewExpired(today); }

    public boolean appliesTo(String contractType) {
        if (appliesToTypes == null) return true;
        return contractType != null && Arrays.asList(appliesToTypes.split(",")).contains(contractType.toUpperCase());
    }
}

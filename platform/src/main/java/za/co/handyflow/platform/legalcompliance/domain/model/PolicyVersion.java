package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * One version of a policy's content. DRAFT → IN_REVIEW → APPROVED → PUBLISHED → SUPERSEDED.
 *  - Content can only be edited in DRAFT. Submitting records a SHA-256 hash; approval and publication re-check it.
 *  - The approver must not be the author (unconditional). A reviewer can return it to DRAFT with a note; the return count is kept.
 *  - Publishing is a separate step from approval so the effective date can be chosen.
 */
@Entity
@Table(name = "legalcompliance_policy_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyVersion extends AggregateRoot<PolicyVersion> {

    public static final int MIN_CONTENT = 50;
    public static final int MIN_NOTE = 15;

    @Column(name = "policy_id", nullable = false) private UUID policyId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private PolicyVersionStatus status = PolicyVersionStatus.DRAFT;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "change_summary", columnDefinition = "TEXT") private String changeSummary;
    @Column(name = "author_id") private UUID authorId;
    @Column(name = "author_name", length = 255) private String authorName;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "content_hash", length = 64) private String contentHash;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_by_name", length = 255) private String approvedByName;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "review_note", columnDefinition = "TEXT") private String reviewNote;
    @Column(name = "returned_count", nullable = false) private int returnedCount;
    @Column(name = "effective_date") private LocalDate effectiveDate;
    @Column(name = "published_by_name", length = 255) private String publishedByName;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "superseded_at") private Instant supersededAt;

    public static PolicyVersion draft(TenantId tenantId, UUID policyId, int number, String content, String changeSummary, UUID by, String byName) {
        PolicyVersion v = new PolicyVersion();
        v.initTenantId(tenantId);
        v.policyId = policyId;
        v.versionNumber = number;
        v.authorId = by;
        v.authorName = byName;
        v.write(content, changeSummary);
        return v;
    }

    /** Whoever last edits the draft becomes its author, so the approval check cannot be dodged by editing someone else's draft and then approving it. */
    public void edit(String content, String changeSummary, UUID by, String byName) {
        if (status != PolicyVersionStatus.DRAFT) throw new IllegalStateException("Only a draft can be edited — current: " + status);
        write(content, changeSummary);
        this.authorId = by;
        this.authorName = byName;
    }

    private void write(String content, String changeSummary) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("The policy text is required");
        this.content = content.strip();
        this.changeSummary = changeSummary == null || changeSummary.isBlank() ? null : changeSummary.trim();
    }

    public void submit() {
        if (status != PolicyVersionStatus.DRAFT) throw new IllegalStateException("Only a draft can be submitted — current: " + status);
        if (content.length() < MIN_CONTENT) throw new IllegalArgumentException("The policy text must be at least " + MIN_CONTENT + " characters before it can be submitted");
        if (versionNumber > 1 && (changeSummary == null || changeSummary.length() < MIN_NOTE))
            throw new IllegalArgumentException("Summarise what changed from the previous version (at least " + MIN_NOTE + " characters)");
        this.contentHash = hash(content);
        this.submittedAt = Instant.now();
        this.status = PolicyVersionStatus.IN_REVIEW;
    }

    public void approve(UUID by, String byName, String note) {
        requireReviewable(by);
        requireHashIntact();
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = Instant.now();
        this.reviewNote = note == null || note.isBlank() ? null : note.trim();
        this.status = PolicyVersionStatus.APPROVED;
    }

    public void returnForRework(UUID by, String byName, String note) {
        requireReviewable(by);
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Say what must change (at least " + MIN_NOTE + " characters)");
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = Instant.now();
        this.reviewNote = note.trim();
        this.returnedCount++;
        this.contentHash = null;
        this.submittedAt = null;
        this.status = PolicyVersionStatus.DRAFT;
    }

    public void publish(String byName, LocalDate effective) {
        if (status != PolicyVersionStatus.APPROVED) throw new IllegalStateException("Only an approved version can be published — current: " + status);
        requireHashIntact();
        this.effectiveDate = effective == null ? LocalDate.now() : effective;
        this.publishedByName = byName;
        this.publishedAt = Instant.now();
        this.status = PolicyVersionStatus.PUBLISHED;
    }

    public void supersede() {
        if (status != PolicyVersionStatus.PUBLISHED) throw new IllegalStateException("Only a published version can be superseded — current: " + status);
        this.supersededAt = Instant.now();
        this.status = PolicyVersionStatus.SUPERSEDED;
    }

    public boolean isOpen() { return status == PolicyVersionStatus.DRAFT || status == PolicyVersionStatus.IN_REVIEW || status == PolicyVersionStatus.APPROVED; }

    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private void requireReviewable(UUID by) {
        if (status != PolicyVersionStatus.IN_REVIEW) throw new IllegalStateException("Only a version in review can be approved or returned — current: " + status);
        if (by != null && by.equals(authorId)) throw new IllegalStateException("Segregation of duties: you wrote this version, so you cannot approve or return it");
    }

    private void requireHashIntact() {
        if (contentHash == null || !contentHash.equals(hash(content))) throw new IllegalStateException("The policy text no longer matches what was submitted for review");
    }
}

package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** A governance committee (board, audit, risk, social and ethics, compliance). Quorum is the minimum number of members present for a meeting to count. */
@Entity
@Table(name = "legalcompliance_committees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Committee extends AggregateRoot<Committee> {

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "committee_type", nullable = false, length = 15) private CommitteeType committeeType;
    @Column(columnDefinition = "TEXT") private String charter;
    @Column(name = "meeting_frequency_months", nullable = false) private int meetingFrequencyMonths = 3;
    @Column(name = "quorum_min", nullable = false) private int quorumMin = 2;
    @Column(name = "charter_review_months", nullable = false) private int charterReviewMonths = 12;
    @Column(name = "last_charter_review") private LocalDate lastCharterReview;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private CommitteeStatus status = CommitteeStatus.ACTIVE;
    @Column(name = "dissolved_reason", columnDefinition = "TEXT") private String dissolvedReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static Committee create(TenantId tenantId, String reference, String name, CommitteeType type, String charter, int frequencyMonths, int quorumMin, int charterReviewMonths, UUID by, String byName) {
        Committee c = new Committee();
        c.initTenantId(tenantId);
        c.reference = reference;
        c.apply(name, type, charter, frequencyMonths, quorumMin, charterReviewMonths);
        c.createdBy = by;
        c.createdByName = byName;
        return c;
    }

    public void update(String name, CommitteeType type, String charter, int frequencyMonths, int quorumMin, int charterReviewMonths) {
        if (status == CommitteeStatus.DISSOLVED) throw new IllegalStateException("A dissolved committee cannot be edited");
        apply(name, type, charter, frequencyMonths, quorumMin, charterReviewMonths);
    }

    private void apply(String name, CommitteeType type, String charter, int frequencyMonths, int quorumMin, int charterReviewMonths) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A name is required");
        if (type == null) throw new IllegalArgumentException("A committee type is required");
        if (frequencyMonths < 1 || frequencyMonths > 12) throw new IllegalArgumentException("Meeting frequency must be between 1 and 12 months");
        if (quorumMin < 1) throw new IllegalArgumentException("Quorum must be at least 1");
        if (charterReviewMonths < 1 || charterReviewMonths > 60) throw new IllegalArgumentException("Charter review must be between 1 and 60 months");
        this.name = name.trim();
        this.committeeType = type;
        this.charter = charter == null || charter.isBlank() ? null : charter.trim();
        this.meetingFrequencyMonths = frequencyMonths;
        this.quorumMin = quorumMin;
        this.charterReviewMonths = charterReviewMonths;
    }

    public void reviewCharter(LocalDate on, LocalDate today) {
        if (status == CommitteeStatus.DISSOLVED) throw new IllegalStateException("A dissolved committee cannot be reviewed");
        if (on == null) throw new IllegalArgumentException("The review date is required");
        if (on.isAfter(today)) throw new IllegalArgumentException("A review cannot be in the future");
        this.lastCharterReview = on;
    }

    public void dissolve(String reason) {
        if (status == CommitteeStatus.DISSOLVED) throw new IllegalStateException("This committee is already dissolved");
        if (reason == null || reason.trim().length() < 15) throw new IllegalArgumentException("Say why the committee is being dissolved (at least 15 characters)");
        this.dissolvedReason = reason.trim();
        this.status = CommitteeStatus.DISSOLVED;
    }
}

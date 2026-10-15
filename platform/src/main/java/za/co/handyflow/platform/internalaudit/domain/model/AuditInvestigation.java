package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** An investigation case file. OPEN <-> UNDER_REVIEW, then CLOSED for good (a closed case takes no further entries). */
@Entity(name = "AuditInvestigation")
@Table(name = "audit_investigations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditInvestigation {
    public static final int MIN_REASON = 10;

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "number", nullable = false) private String number;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "summary", columnDefinition = "TEXT") private String summary;
    @Column(name = "status", nullable = false) private String status = "OPEN";
    @Column(name = "opened_by", nullable = false) private UUID openedBy;
    @Column(name = "opened_at", nullable = false) private Instant openedAt = Instant.now();
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "close_reason", columnDefinition = "TEXT") private String closeReason;

    public static String numberFor(int sequence) { return String.format("INV-%04d", sequence); }

    public static AuditInvestigation open(UUID tenantId, String number, String title, String summary, UUID by) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A case title is required");
        AuditInvestigation i = new AuditInvestigation();
        i.tenantId = tenantId; i.number = number; i.title = title.trim(); i.summary = summary == null || summary.isBlank() ? null : summary.trim(); i.openedBy = by;
        return i;
    }

    public boolean isClosed() { return "CLOSED".equals(status); }

    /** Writes of any kind (notes, evidence, member changes) need an open case. */
    public void requireWritable() { if (isClosed()) throw new IllegalStateException("This case is closed"); }

    public void setReview(boolean underReview) {
        requireWritable();
        this.status = underReview ? "UNDER_REVIEW" : "OPEN";
    }

    public void close(String reason, UUID by) {
        requireWritable();
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give the reason for closing (at least " + MIN_REASON + " characters)");
        this.status = "CLOSED"; this.closeReason = reason.trim(); this.closedBy = by; this.closedAt = Instant.now();
    }
}

package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One reviewer's answer to one checklist item. "No" and "Not applicable" must be explained. */
@Entity(name = "AuditQualityCheck")
@Table(name = "audit_quality_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditQualityCheck {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "review_id", nullable = false) private UUID reviewId;
    @Column(name = "item_code", nullable = false) private String itemCode;
    @Column(name = "result", nullable = false) private String result;
    @Column(name = "note", columnDefinition = "TEXT") private String note;
    @Column(name = "answered_by", nullable = false) private UUID answeredBy;
    @Column(name = "answered_at", nullable = false) private Instant answeredAt = Instant.now();

    public static AuditQualityCheck answer(UUID tenantId, UUID reviewId, String code, String result, String note, UUID by) {
        AuditQualityCheck c = new AuditQualityCheck();
        c.tenantId = tenantId; c.reviewId = reviewId; c.itemCode = code;
        c.set(result, note, by);
        return c;
    }

    public void set(String newResult, String newNote, UUID by) {
        String r = newResult == null ? "" : newResult.toUpperCase();
        if (!r.equals("YES") && !r.equals("NO") && !r.equals("NA")) throw new IllegalArgumentException("Answer must be YES, NO or NA");
        String n = newNote == null ? "" : newNote.trim();
        if (!r.equals("YES") && n.length() < AuditQualityReview.MIN_TEXT)
            throw new IllegalArgumentException((r.equals("NO") ? "Say what is missing" : "Say why it does not apply") + " (at least " + AuditQualityReview.MIN_TEXT + " characters)");
        this.result = r; this.note = n.isEmpty() ? null : n; this.answeredBy = by; this.answeredAt = Instant.now();
    }
}

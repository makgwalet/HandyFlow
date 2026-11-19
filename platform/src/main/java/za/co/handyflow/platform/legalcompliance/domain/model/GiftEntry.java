package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A gift or hospitality given or received. Above the threshold that applied on the day (kept on the row) it is PENDING until someone other than the
 * declarant approves or declines it; at or below, it is simply RECORDED. A declined entry needs a note saying what happens to it (returned, donated, paid back).
 */
@Entity
@Table(name = "legalcompliance_gift_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GiftEntry extends AggregateRoot<GiftEntry> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private GiftDirection direction;
    @Column(name = "declarant_id", nullable = false) private UUID declarantId;
    @Column(name = "declarant_name", nullable = false, length = 255) private String declarantName;
    @Column(nullable = false, length = 255) private String counterparty;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(length = 255) private String occasion;
    @Column(name = "entry_date", nullable = false) private LocalDate entryDate;
    @Column(name = "value_zar", nullable = false, precision = 12, scale = 2) private BigDecimal valueZar;
    @Column(name = "threshold_zar", nullable = false, precision = 12, scale = 2) private BigDecimal thresholdZar;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private GiftStatus status;
    @Column(name = "decided_by_name", length = 255) private String decidedByName;
    @Column(name = "decided_on") private LocalDate decidedOn;
    @Column(name = "decision_note", columnDefinition = "TEXT") private String decisionNote;

    public static GiftEntry record(TenantId tenantId, String reference, GiftDirection direction, UUID declarantId, String declarantName, String counterparty, String description,
                                   String occasion, LocalDate entryDate, BigDecimal value, BigDecimal threshold, LocalDate today) {
        if (direction == null) throw new IllegalArgumentException("Say whether it was given or received");
        if (declarantId == null) throw new IllegalArgumentException("The person is required");
        if (counterparty == null || counterparty.isBlank()) throw new IllegalArgumentException("Say who it was given by or to");
        if (description == null || description.trim().length() < 10) throw new IllegalArgumentException("Describe it (at least 10 characters)");
        if (entryDate == null) throw new IllegalArgumentException("The date is required");
        if (entryDate.isAfter(today)) throw new IllegalArgumentException("The date cannot be in the future");
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("The value (in rand) is required and cannot be negative");
        GiftEntry g = new GiftEntry();
        g.initTenantId(tenantId);
        g.reference = reference;
        g.direction = direction;
        g.declarantId = declarantId;
        g.declarantName = declarantName;
        g.counterparty = counterparty.trim();
        g.description = description.trim();
        g.occasion = occasion == null || occasion.isBlank() ? null : occasion.trim();
        g.entryDate = entryDate;
        g.valueZar = value;
        g.thresholdZar = threshold;
        g.status = value.compareTo(threshold) > 0 ? GiftStatus.PENDING : GiftStatus.RECORDED;
        return g;
    }

    public void approve(UUID by, String byName, String note, LocalDate today) {
        decide(GiftStatus.APPROVED, by, byName, note, today);
    }

    public void decline(UUID by, String byName, String note, LocalDate today) {
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say what happens to it — returned, donated or paid back (at least " + MIN_TEXT + " characters)");
        decide(GiftStatus.DECLINED, by, byName, note, today);
    }

    private void decide(GiftStatus to, UUID by, String byName, String note, LocalDate today) {
        if (status != GiftStatus.PENDING) throw new IllegalStateException("Only an entry awaiting approval can be decided — current: " + status);
        if (by != null && by.equals(declarantId)) throw new IllegalStateException("Segregation of duties: you cannot approve or decline your own entry");
        this.status = to;
        this.decidedByName = byName;
        this.decidedOn = today;
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
    }
}

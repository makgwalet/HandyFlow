package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One drawn item from a SamplingPlan. journalEntryId references the
 * real AccJournalEntry (the confirmed GL-focused scope) — snapshot
 * fields (entryNumber/entryDate/amount) are frozen at the moment of
 * selection, matching real audit workpaper practice: the sample's own
 * record of what it drew shouldn't silently change if the underlying
 * journal entry is edited later.
 */
@Entity
@Table(name = "audit_sample_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SampleItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "sampling_plan_id", nullable = false) private UUID samplingPlanId;
    @Column(name = "journal_entry_id", nullable = false) private UUID journalEntryId;
    @Column(name = "entry_number_snapshot") private String entryNumberSnapshot;
    @Column(name = "entry_date_snapshot") private LocalDate entryDateSnapshot;
    @Column(name = "amount_snapshot") private BigDecimal amountSnapshot;
    @Column(name = "notes") private String notes;
    @Column(name = "selected_at", nullable = false, updatable = false) private Instant selectedAt;

    public static SampleItem create(UUID tenantId, UUID samplingPlanId, UUID journalEntryId,
                                    String entryNumberSnapshot, LocalDate entryDateSnapshot, BigDecimal amountSnapshot) {
        SampleItem s = new SampleItem();
        s.tenantId = tenantId;
        s.samplingPlanId = samplingPlanId;
        s.journalEntryId = journalEntryId;
        s.entryNumberSnapshot = entryNumberSnapshot;
        s.entryDateSnapshot = entryDateSnapshot;
        s.amountSnapshot = amountSnapshot;
        s.selectedAt = Instant.now();
        return s;
    }

    public void updateNotes(String notes) {
        this.notes = notes;
    }
}

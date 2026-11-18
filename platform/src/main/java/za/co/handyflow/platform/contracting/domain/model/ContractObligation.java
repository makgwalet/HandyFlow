package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Something a contract requires one side to do by a date. Entered by staff; not extracted from the contract text. */
@Entity
@Table(name = "contract_obligations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractObligation {

    public static final Set<String> TYPES = Set.of("PAYMENT", "DELIVERY", "REPORTING", "COMPLIANCE", "NOTICE", "OTHER");
    public static final Set<String> SIDES = Set.of("OURS", "COUNTERPARTY");
    public static final Set<String> RECURRENCES = Set.of("NONE", "MONTHLY", "QUARTERLY", "ANNUALLY");

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "obligation_type") private String obligationType;
    @Column(name = "responsible_side") private String responsibleSide;
    @Column(name = "owner_email") private String ownerEmail;
    @Column(name = "due_date") private LocalDate dueDate;
    private String recurrence;
    private String status;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completed_by") private UUID completedBy;
    @Column(name = "completion_note", columnDefinition = "TEXT") private String completionNote;
    @Column(name = "waived_reason", columnDefinition = "TEXT") private String waivedReason;
    @Column(name = "last_reminded_on") private LocalDate lastRemindedOn;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static ContractObligation create(UUID tenantId, UUID contractId, String title, String description, String type,
                                            String side, String ownerEmail, LocalDate dueDate, String recurrence, UUID createdBy) {
        ContractObligation o = new ContractObligation();
        o.id = UUID.randomUUID();
        o.tenantId = tenantId;
        o.contractId = contractId;
        o.status = "OPEN";
        o.createdBy = createdBy;
        o.createdAt = Instant.now();
        o.applyDetails(title, description, type, side, ownerEmail, dueDate, recurrence);
        return o;
    }

    /** Change the details of an obligation that is still open. */
    public void edit(String title, String description, String type, String side, String ownerEmail, LocalDate dueDate, String recurrence) {
        if (!isOpen()) throw new IllegalStateException("Only an open obligation can be edited (this one is " + status + ")");
        applyDetails(title, description, type, side, ownerEmail, dueDate, recurrence);
    }

    private void applyDetails(String title, String description, String type, String side, String ownerEmail, LocalDate dueDate, String recurrence) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An obligation needs a title");
        if (dueDate == null) throw new IllegalArgumentException("An obligation needs a due date");
        String t = type == null || type.isBlank() ? "OTHER" : type.trim().toUpperCase();
        String s = side == null ? "" : side.trim().toUpperCase();
        String r = recurrence == null || recurrence.isBlank() ? "NONE" : recurrence.trim().toUpperCase();
        if (!TYPES.contains(t)) throw new IllegalArgumentException("Unknown obligation type: " + type);
        if (!SIDES.contains(s)) throw new IllegalArgumentException("Responsible side must be OURS or COUNTERPARTY");
        if (!RECURRENCES.contains(r)) throw new IllegalArgumentException("Unknown recurrence: " + recurrence);
        String email = ownerEmail == null || ownerEmail.isBlank() ? null : ownerEmail.trim();
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("The reminder email address is not valid");
        this.title = title.trim();
        this.description = description;
        this.obligationType = t;
        this.responsibleSide = s;
        this.ownerEmail = email;
        this.dueDate = dueDate;
        this.recurrence = r;
        this.updatedAt = Instant.now();
    }

    public boolean isOpen() { return "OPEN".equals(status) || "IN_PROGRESS".equals(status); }

    public boolean isOverdue(LocalDate today) { return isOpen() && dueDate.isBefore(today); }

    public void start() {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open obligation can be started (this one is " + status + ")");
        status = "IN_PROGRESS";
        updatedAt = Instant.now();
    }

    /**
     * Completes this obligation. A recurring obligation returns the next occurrence, dated from this one's due date (not from
     * the day it was completed) so a late completion does not shift the schedule.
     */
    public Optional<ContractObligation> complete(String note, UUID by) {
        if (!isOpen()) throw new IllegalStateException("Only an open obligation can be completed (this one is " + status + ")");
        status = "COMPLETED";
        completedAt = Instant.now();
        completedBy = by;
        completionNote = note;
        updatedAt = completedAt;
        if ("NONE".equals(recurrence)) return Optional.empty();
        LocalDate next = switch (recurrence) {
            case "MONTHLY" -> dueDate.plusMonths(1);
            case "QUARTERLY" -> dueDate.plusMonths(3);
            default -> dueDate.plusYears(1);
        };
        return Optional.of(create(tenantId, contractId, title, description, obligationType, responsibleSide, ownerEmail, next, recurrence, by));
    }

    public void waive(String reason) {
        if (!isOpen()) throw new IllegalStateException("Only an open obligation can be waived (this one is " + status + ")");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the obligation is being waived");
        status = "WAIVED";
        waivedReason = reason;
        updatedAt = Instant.now();
    }

    public void markReminded(LocalDate today) { this.lastRemindedOn = today; }

    /** Remind at 7 days, 1 day and on the due date; once overdue, again each week. Never twice in one day. */
    public boolean reminderDue(LocalDate today) {
        if (!isOpen() || ownerEmail == null || today.equals(lastRemindedOn)) return false;
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
        if (days == 7 || days == 1 || days == 0) return true;
        if (days < 0) return lastRemindedOn == null || java.time.temporal.ChronoUnit.DAYS.between(lastRemindedOn, today) >= 7;
        return false;
    }
}

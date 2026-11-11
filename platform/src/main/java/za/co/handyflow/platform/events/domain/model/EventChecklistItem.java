package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One thing that must be done, in one phase of the event.
 * <p>
 * CONTINGENCY items are the event's contingency plans: the title is the scenario ("What if the power
 * fails?"), the description is the response, the assignee is the owner, and "done" means the plan
 * has been reviewed. They share this entity because they need exactly the same things — an owner,
 * a required flag, a done state and who/when — and a second entity would add nothing.
 */
@Entity
@Table(name = "event_checklist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventChecklistItem {

    /** In the order an event moves through them; CONTINGENCY sits outside the timeline, last. */
    public static final List<String> PHASES =
            List.of("PRE_EVENT", "SETUP", "EVENT_DAY", "BREAKDOWN", "POST_EVENT", "CONTINGENCY");

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)  private UUID tenantId;
    @Column(name = "event_id", nullable = false)   private UUID eventId;
    @Column(name = "phase", nullable = false)      private String phase;
    @Column(name = "title", nullable = false)      private String title;
    @Column(name = "description")                  private String description;
    @Column(name = "assigned_to")                  private String assignedTo;
    @Column(name = "due_at")                       private LocalDateTime dueAt;
    @Column(name = "required", nullable = false)   private boolean required = true;
    @Column(name = "done", nullable = false)       private boolean done;
    @Column(name = "done_by_name")                 private String doneByName;
    @Column(name = "done_at")                      private Instant doneAt;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static EventChecklistItem create(TenantId tenantId, UUID eventId, String phase, String title,
                                            String description, String assignedTo, LocalDateTime dueAt,
                                            boolean required, int sortOrder) {
        EventChecklistItem c = new EventChecklistItem();
        c.id        = UUID.randomUUID();
        c.tenantId  = tenantId.getValue();
        c.eventId   = eventId;
        c.createdAt = Instant.now();
        c.apply(phase, title, description, assignedTo, dueAt, required, sortOrder);
        return c;
    }

    public void update(String phase, String title, String description, String assignedTo,
                       LocalDateTime dueAt, boolean required, int sortOrder) {
        apply(phase, title, description, assignedTo, dueAt, required, sortOrder);
    }

    private void apply(String phase, String title, String description, String assignedTo,
                       LocalDateTime dueAt, boolean required, int sortOrder) {
        String p = phase == null ? "" : phase.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (!PHASES.contains(p))
            throw new IllegalArgumentException("Unknown phase '" + phase + "'. Valid: " + PHASES);
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("A checklist item needs a title");
        this.phase       = p;
        this.title       = title.trim();
        this.description = description;
        this.assignedTo  = assignedTo;
        this.dueAt       = dueAt;
        this.required    = required;
        this.sortOrder   = sortOrder;
        this.updatedAt   = Instant.now();
    }

    public void markDone(String byName) {
        if (done) return;                      // ticking an already-ticked item is harmless
        this.done       = true;
        this.doneByName = byName;
        this.doneAt     = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void reopen() {
        this.done       = false;
        this.doneByName = null;
        this.doneAt     = null;
        this.updatedAt  = Instant.now();
    }
}
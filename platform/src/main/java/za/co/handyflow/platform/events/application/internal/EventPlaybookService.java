package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.application.internal.ReadinessCalculator.ChecklistRow;
import za.co.handyflow.platform.events.application.internal.ReadinessCalculator.StaffRow;
import za.co.handyflow.platform.events.domain.model.EventChecklistItem;
import za.co.handyflow.platform.events.domain.model.EventContact;
import za.co.handyflow.platform.events.domain.model.EventSession;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * The event's playbook: emergency contacts, the checklist (contingency plans included), a starter
 * template for the checklist, and the readiness check that pulls these together with the roster and
 * run sheet into one "are we ready?" answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPlaybookService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final EventRepository              eventRepo;
    private final EventContactRepository       contactRepo;
    private final EventChecklistItemRepository checklistRepo;
    private final EventStaffRepository         staffRepo;
    private final EventSessionRepository       sessionRepo;
    private final EventAuditService            audit;

    // ══════════════════════════════════════════════════════════════════════════
    // Emergency contacts
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts(TenantId tenantId, UUID eventId) {
        requireEvent(tenantId, eventId);
        return contactRepo.findByEvent(eventId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ContactResponse addContact(TenantId tenantId, UUID eventId, ContactRequest req) {
        requireEvent(tenantId, eventId);
        EventContact c = EventContact.create(tenantId, eventId, req.contactType(), req.name(), req.organisation(),
                req.phone(), req.alternatePhone(), req.email(), req.notes());
        contactRepo.save(c);
        audit.record(tenantId, eventId, "CONTACT_ADDED", "CONTACT", c.getId(),
                "Emergency contact added: " + c.getContactType());
        return toResponse(c);
    }

    @Transactional
    public ContactResponse updateContact(TenantId tenantId, UUID eventId, UUID contactId, ContactRequest req) {
        requireEvent(tenantId, eventId);
        EventContact c = contactRepo.findByIdAndEvent(contactId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId.toString()));
        c.update(req.contactType(), req.name(), req.organisation(), req.phone(), req.alternatePhone(),
                req.email(), req.notes());
        contactRepo.save(c);
        audit.record(tenantId, eventId, "CONTACT_UPDATED", "CONTACT", c.getId(),
                "Emergency contact updated: " + c.getContactType());
        return toResponse(c);
    }

    @Transactional
    public void deleteContact(TenantId tenantId, UUID eventId, UUID contactId) {
        requireEvent(tenantId, eventId);
        EventContact c = contactRepo.findByIdAndEvent(contactId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId.toString()));
        contactRepo.delete(c);
        audit.record(tenantId, eventId, "CONTACT_DELETED", "CONTACT", contactId,
                "Emergency contact removed: " + c.getContactType());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Checklist
    // ══════════════════════════════════════════════════════════════════════════

    /** Items grouped in the order an event moves through its phases; within a phase by sort order, then age. */
    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> listChecklist(TenantId tenantId, UUID eventId) {
        requireEvent(tenantId, eventId);
        LocalDateTime now = LocalDateTime.now(SAST);
        List<EventChecklistItem> items = new ArrayList<>(checklistRepo.findByEvent(eventId));
        items.sort(Comparator.comparingInt(i -> EventChecklistItem.PHASES.indexOf(i.getPhase())));   // stable: keeps repo order within a phase
        return items.stream().map(i -> toResponse(i, now)).toList();
    }

    @Transactional
    public ChecklistItemResponse addItem(TenantId tenantId, UUID eventId, ChecklistItemRequest req) {
        requireEvent(tenantId, eventId);
        String phase = normalisePhase(req.phase());
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : nextSortOrder(eventId, phase);

        EventChecklistItem item = EventChecklistItem.create(tenantId, eventId, phase, req.title(), req.description(),
                req.assignedTo(), req.dueAt(), req.required() == null || req.required(), sortOrder);
        checklistRepo.save(item);
        audit.record(tenantId, eventId, "CHECKLIST_ITEM_ADDED", "CHECKLIST", item.getId(),
                "Checklist item added to " + item.getPhase() + (item.isRequired() ? " (required)" : ""));
        return toResponse(item, LocalDateTime.now(SAST));
    }

    @Transactional
    public ChecklistItemResponse updateItem(TenantId tenantId, UUID eventId, UUID itemId, ChecklistItemRequest req) {
        requireEvent(tenantId, eventId);
        EventChecklistItem item = findItem(eventId, itemId);
        item.update(req.phase(), req.title(), req.description(), req.assignedTo(), req.dueAt(),
                req.required() == null || req.required(),
                req.sortOrder() != null ? req.sortOrder() : item.getSortOrder());
        checklistRepo.save(item);
        audit.record(tenantId, eventId, "CHECKLIST_ITEM_UPDATED", "CHECKLIST", item.getId(),
                "Checklist item updated in " + item.getPhase());
        return toResponse(item, LocalDateTime.now(SAST));
    }

    @Transactional
    public void deleteItem(TenantId tenantId, UUID eventId, UUID itemId) {
        requireEvent(tenantId, eventId);
        EventChecklistItem item = findItem(eventId, itemId);
        checklistRepo.delete(item);
        audit.record(tenantId, eventId, "CHECKLIST_ITEM_DELETED", "CHECKLIST", itemId,
                "Checklist item removed from " + item.getPhase());
    }

    @Transactional
    public ChecklistItemResponse completeItem(TenantId tenantId, UUID eventId, UUID itemId) {
        requireEvent(tenantId, eventId);
        EventChecklistItem item = findItem(eventId, itemId);
        if (!item.isDone()) {
            item.markDone(TenantContext.getCurrentUserName());
            checklistRepo.save(item);
            audit.record(tenantId, eventId, "CHECKLIST_ITEM_DONE", "CHECKLIST", itemId,
                    ("CONTINGENCY".equals(item.getPhase()) ? "Contingency plan reviewed" : "Checklist item done")
                            + " in " + item.getPhase());
        }
        return toResponse(item, LocalDateTime.now(SAST));
    }

    @Transactional
    public ChecklistItemResponse reopenItem(TenantId tenantId, UUID eventId, UUID itemId) {
        requireEvent(tenantId, eventId);
        EventChecklistItem item = findItem(eventId, itemId);
        if (item.isDone()) {
            item.reopen();
            checklistRepo.save(item);
            audit.record(tenantId, eventId, "CHECKLIST_ITEM_REOPENED", "CHECKLIST", itemId,
                    "Checklist item reopened in " + item.getPhase());
        }
        return toResponse(item, LocalDateTime.now(SAST));
    }

    /**
     * Adds a starter checklist (and starter contingency plans) for a typical South African event.
     * Safe to run repeatedly: an item is skipped if the event already has one with the same phase and
     * title (ignoring case), so re-running never duplicates and never touches items you've edited.
     * The contingency descriptions are prompts to adapt, not finished plans — and they start
     * un-reviewed, so the readiness check stays red until a person has actually read them.
     */
    @Transactional
    public ChecklistTemplateResult applyTemplate(TenantId tenantId, UUID eventId) {
        requireEvent(tenantId, eventId);
        Set<String> existing = new HashSet<>();
        Map<String, Integer> nextOrder = new HashMap<>();
        for (EventChecklistItem i : checklistRepo.findByEvent(eventId)) {
            existing.add(key(i.getPhase(), i.getTitle()));
            nextOrder.merge(i.getPhase(), i.getSortOrder() + 1, Math::max);
        }

        int added = 0, skipped = 0;
        for (TemplateItem t : TEMPLATE) {
            if (!existing.add(key(t.phase(), t.title()))) {
                skipped++;
                continue;
            }
            int order = nextOrder.getOrDefault(t.phase(), 1);      // end of the phase, or the start if it's empty
            nextOrder.put(t.phase(), order + 1);
            checklistRepo.save(EventChecklistItem.create(tenantId, eventId, t.phase(), t.title(), t.description(),
                    null, null, t.required(), order));
            added++;
        }
        audit.record(tenantId, eventId, "CHECKLIST_TEMPLATE_APPLIED", "CHECKLIST", null,
                "Starter checklist applied: " + added + " added, " + skipped + " already present");
        return new ChecklistTemplateResult(added, skipped);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Readiness
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ReadinessResponse readiness(TenantId tenantId, UUID eventId) {
        requireEvent(tenantId, eventId);

        List<String> contactTypes = contactRepo.findByEvent(eventId).stream().map(EventContact::getContactType).toList();
        List<StaffRow> staff = staffRepo.findByEvent(eventId).stream()
                .map(a -> new StaffRow(a.getRole(), a.getStatus())).toList();
        List<ChecklistRow> checklist = checklistRepo.findByEvent(eventId).stream()
                .map(i -> new ChecklistRow(i.getPhase(), i.isRequired(), i.isDone())).toList();
        long activeSessions = sessionRepo.findByEvent(eventId).stream().filter(EventSession::isActive).count();

        List<ReadinessResponse.ReadinessCheck> checks =
                ReadinessCalculator.evaluate(contactTypes, staff, checklist, activeSessions);
        return new ReadinessResponse(checks.stream().allMatch(ReadinessResponse.ReadinessCheck::passed),
                checks, ReadinessCalculator.progress(checklist));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void requireEvent(TenantId tenantId, UUID eventId) {
        eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private EventChecklistItem findItem(UUID eventId, UUID itemId) {
        return checklistRepo.findByIdAndEvent(itemId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item", itemId.toString()));
    }

    private int nextSortOrder(UUID eventId, String phase) {
        Integer max = checklistRepo.maxSortOrder(eventId, phase);
        return max == null ? 1 : max + 1;
    }

    private static String normalisePhase(String phase) {
        return phase == null ? "" : phase.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static String key(String phase, String title) {
        return phase + "|" + title.trim().toLowerCase(Locale.ROOT);
    }

    private ContactResponse toResponse(EventContact c) {
        return new ContactResponse(c.getId(), c.getContactType(), c.getName(), c.getOrganisation(), c.getPhone(),
                c.getAlternatePhone(), c.getEmail(), c.getNotes());
    }

    private ChecklistItemResponse toResponse(EventChecklistItem i, LocalDateTime now) {
        boolean overdue = !i.isDone() && i.getDueAt() != null && i.getDueAt().isBefore(now);
        return new ChecklistItemResponse(i.getId(), i.getPhase(), i.getTitle(), i.getDescription(), i.getAssignedTo(),
                i.getDueAt(), i.isRequired(), i.isDone(), i.getDoneByName(), i.getDoneAt(), i.getSortOrder(), overdue);
    }

    // ── The starter template ──────────────────────────────────────────────────

    private record TemplateItem(String phase, String title, String description, boolean required) {}

    private static final List<TemplateItem> TEMPLATE = List.of(
            // Before the event
            new TemplateItem("PRE_EVENT", "Confirm the venue booking and access times", null, true),
            new TemplateItem("PRE_EVENT", "Confirm public liability insurance is in place", null, true),
            new TemplateItem("PRE_EVENT", "Confirm first-aid / medical cover for the expected numbers", null, true),
            new TemplateItem("PRE_EVENT", "Confirm security arrangements, using a PSiRA-registered provider", null, true),
            new TemplateItem("PRE_EVENT", "Confirm the venue capacity limit and that the fire-safety certificate is displayed", null, true),
            new TemplateItem("PRE_EVENT", "Confirm AV and equipment requirements with suppliers", null, true),
            new TemplateItem("PRE_EVENT", "Send final instructions to guests and suppliers", null, true),
            new TemplateItem("PRE_EVENT", "Brief staff and volunteers", null, true),
            new TemplateItem("PRE_EVENT", "Confirm catering numbers and dietary requirements", null, false),
            // Setup
            new TemplateItem("SETUP", "Walk the venue: exits, fire equipment and evacuation routes", null, true),
            new TemplateItem("SETUP", "Test sound, lighting and projection", null, true),
            new TemplateItem("SETUP", "Set up the registration desk and check-in scanners", null, true),
            new TemplateItem("SETUP", "Hold the pre-event briefing with the whole team", null, true),
            new TemplateItem("SETUP", "Put up signage and wayfinding", null, false),
            // On the day
            new TemplateItem("EVENT_DAY", "Open the doors and confirm check-in is running", null, true),
            new TemplateItem("EVENT_DAY", "Keep checking attendance against the venue limit", null, true),
            new TemplateItem("EVENT_DAY", "Keep the incident log up to date", null, true),
            // Breakdown
            new TemplateItem("BREAKDOWN", "Collect equipment and check it against the allocation list", null, true),
            new TemplateItem("BREAKDOWN", "Hand the venue back and note any damage", null, true),
            new TemplateItem("BREAKDOWN", "Check lost property", null, false),
            // After the event
            new TemplateItem("POST_EVENT", "Reconcile attendance and review the no-show list", null, true),
            new TemplateItem("POST_EVENT", "Capture supplier invoices and confirm they match the commitments", null, true),
            new TemplateItem("POST_EVENT", "Send thank-you messages and ask for feedback", null, false),
            new TemplateItem("POST_EVENT", "Hold a debrief and record lessons learnt", null, false),
            // Contingency plans — prompts to adapt. Done = reviewed.
            new TemplateItem("CONTINGENCY", "What if the power fails?",
                    "Starting point — adapt it: who calls the venue/electrician, is there a generator or UPS for sound and check-in, "
                            + "who makes the announcement, and how does check-in carry on without power (e.g. a printed guest list)?", true),
            new TemplateItem("CONTINGENCY", "What if a guest needs urgent medical help?",
                    "Starting point — adapt it: who is the first-aider, where is the first-aid point, who calls the paramedics "
                            + "(see the emergency contacts), and who meets the ambulance at the gate?", true),
            new TemplateItem("CONTINGENCY", "What if the venue must be evacuated?",
                    "Starting point — adapt it: who gives the order, which exits and assembly point, who sweeps each area, "
                            + "and who holds the guest list for a head-count?", true),
            new TemplateItem("CONTINGENCY", "What if a key speaker or performer doesn't arrive?",
                    "Starting point — adapt it: who is the backup, who can re-order the programme, and who tells the audience?", false),
            new TemplateItem("CONTINGENCY", "What if it rains?",
                    "Starting point — for outdoor events: where do guests shelter, how is equipment protected, "
                            + "and what is the point at which the programme is moved or paused?", false)
    );
}

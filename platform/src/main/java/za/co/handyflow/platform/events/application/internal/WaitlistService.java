package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventTicketTier;
import za.co.handyflow.platform.events.domain.model.EventWaitlistEntry;
import za.co.handyflow.platform.events.domain.repository.EventGuestRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.domain.repository.EventTicketTierRepository;
import za.co.handyflow.platform.events.domain.repository.EventWaitlistRepository;
import za.co.handyflow.platform.events.dto.GuestResponse;
import za.co.handyflow.platform.events.dto.JoinWaitlistRequest;
import za.co.handyflow.platform.events.dto.WaitlistEntryResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FIFO waitlist for full events and full ticket tiers.
 * <p>
 * PROMOTION is automatic: whenever room appears (a guest cancels, or venue
 * capacity is raised) EventsService calls {@link #promoteEligible}, which
 * walks the queue oldest-first and registers whoever now fits, through the
 * same GuestRegistrationEngine every other registration uses. A promoted
 * person receives their ticket by email exactly like a normal registration.
 * <p>
 * Promotion happens under the event row lock held by the caller, so it can't
 * race with a simultaneous new registration grabbing the freed seat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    /** Upper bound on queue entries examined per promotion pass — keeps one cancellation cheap. */
    private static final int PROMOTION_BATCH = 200;

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository      guestRepo;
    private final EventWaitlistRepository   waitlistRepo;
    private final GuestRegistrationEngine   engine;
    private final EmailService              emailService;
    private final EventAuditService         audit;

    // ── Join ──────────────────────────────────────────────────────────────────

    /**
     * Adds someone to the waitlist. Only allowed when they genuinely could not
     * register right now — otherwise they're told to register directly, so the
     * queue never holds people who could simply have a ticket.
     */
    @Transactional
    public WaitlistEntryResponse join(TenantId tenantId, UUID eventId, JoinWaitlistRequest req) {
        Event event = lockEvent(tenantId, eventId);
        engine.assertOpen(event, LocalDateTime.now(SAST), false);

        String fullName = req.fullName().trim();
        String email    = GuestRegistrationEngine.normalizeEmail(req.email());

        if (guestRepo.existsActiveDuplicate(eventId, email, fullName)) {
            throw new HandyFlowException(fullName + " (" + email + ") is already registered for this event",
                    HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION");
        }
        if (waitlistRepo.existsWaiting(eventId, email, fullName)) {
            throw new HandyFlowException(fullName + " (" + email + ") is already on the waitlist",
                    HttpStatus.CONFLICT, "ALREADY_WAITLISTED");
        }

        EventTicketTier tier = null;
        if (req.tierId() != null) {
            final UUID tierId = req.tierId();
            tier = tierRepo.findByIdAndEvent(tierId, eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tier", tierId.toString()));
            if (!tier.isActive()) {
                throw new HandyFlowException("Ticket tier '" + tier.getName() + "' is not available",
                        HttpStatus.CONFLICT, "TIER_NOT_ON_SALE");
            }
        }

        if (!engine.isFull(event, tier)) {
            throw new HandyFlowException(
                    "Places are still available — register directly instead of joining the waitlist",
                    HttpStatus.CONFLICT, "SEATS_AVAILABLE");
        }

        EventWaitlistEntry entry = EventWaitlistEntry.join(tenantId, eventId,
                req.tierId(), req.customerId(), fullName, email,
                req.phone(), req.company(), req.dietaryRequirements());
        waitlistRepo.save(entry);

        long position = waitlistRepo.countWaiting(eventId);   // newest entry is last in the queue
        audit.record(tenantId, eventId, "WAITLIST_JOINED", "WAITLIST", entry.getId(),
                "Guest joined the waitlist (position " + position + ")"
                        + (tier != null ? " for " + tier.getName() : ""));

        final String title = event.getTitle();
        AfterCommit.run(() -> sendJoinedEmail(email, fullName, title));

        return toResponse(entry, tier != null ? tier.getName() : null, (int) position);
    }

    // ── Read / remove ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WaitlistEntryResponse> list(TenantId tenantId, UUID eventId, String status, Pageable pageable) {
        eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        String wanted = (status == null || status.isBlank()) ? "WAITING" : status.trim().toUpperCase();
        Page<EventWaitlistEntry> page = waitlistRepo.findByEventAndStatus(eventId, wanted, pageable);
        Map<UUID, String> tierNames = loadTierNames(eventId);

        List<WaitlistEntryResponse> rows = new ArrayList<>();
        int index = 0;
        for (EventWaitlistEntry e : page.getContent()) {
            Integer position = "WAITING".equals(wanted) ? (int) pageable.getOffset() + index + 1 : null;
            rows.add(toResponse(e, e.getTierId() != null ? tierNames.getOrDefault(e.getTierId(), "—") : null, position));
            index++;
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    @Transactional
    public void remove(TenantId tenantId, UUID eventId, UUID entryId) {
        eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        EventWaitlistEntry entry = waitlistRepo.findByIdAndEvent(entryId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry", entryId.toString()));
        if (!entry.isWaiting()) {
            throw new HandyFlowException("Only entries still WAITING can be removed (this one is "
                    + entry.getStatus() + ")", HttpStatus.CONFLICT, "WAITLIST_ENTRY_CLOSED");
        }
        entry.remove();
        waitlistRepo.save(entry);
        audit.record(tenantId, eventId, "WAITLIST_REMOVED", "WAITLIST", entryId,
                "Waitlist entry removed by staff");
    }

    /** Called when an event is completed or cancelled — nobody can be promoted into a finished event. */
    public void closeWaitlist(UUID eventId) {
        int closed = waitlistRepo.removeAllWaiting(eventId, Instant.now());
        if (closed > 0) log.info("[Events] Closed {} waiting entr(ies) for event={}", closed, eventId);
    }

    // ── Promotion ─────────────────────────────────────────────────────────────

    /**
     * Staff-triggered promotion of one specific entry. Staff may override the
     * registration deadline and the tier's sale window, but never capacity —
     * a seat must really exist, otherwise the engine's 409 comes straight back.
     */
    @Transactional
    public GuestResponse promote(TenantId tenantId, UUID eventId, UUID entryId) {
        Event event = lockEvent(tenantId, eventId);
        EventWaitlistEntry entry = waitlistRepo.findByIdAndEvent(entryId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry", entryId.toString()));
        if (!entry.isWaiting()) {
            throw new HandyFlowException("Only entries still WAITING can be promoted (this one is "
                    + entry.getStatus() + ")", HttpStatus.CONFLICT, "WAITLIST_ENTRY_CLOSED");
        }

        GuestRegistrationEngine.Placed placed = engine.register(tenantId, event, commandFor(entry, true));
        entry.promote(placed.guest().getId());
        waitlistRepo.save(entry);
        log.info("[Events] Waitlist entry promoted manually event={} ticket={}",
                event.getEventNumber(), placed.guest().getTicketNumber());

        return GuestMapper.toResponse(placed.guest(), loadTierNames(eventId));
    }

    /**
     * Automatic promotion — registers as many waiting people as now fit,
     * oldest first. MUST be called with the event row already locked, inside
     * the transaction that freed the room.
     * <p>
     * Per-entry outcome of a refused placement:
     * <ul>
     *   <li>TIER_SOLD_OUT / TIER_NOT_ON_SALE — that person's tier has no room;
     *       skip them (someone behind them may want a different tier) and continue.</li>
     *   <li>DUPLICATE_REGISTRATION / not-found — they're already registered, or their
     *       tier is gone; remove the stale entry and continue.</li>
     *   <li>anything else (venue full, registration closed or not open) — nobody
     *       further down the queue can be placed either; stop.</li>
     * </ul>
     *
     * @return how many people were promoted
     */
    public int promoteEligible(TenantId tenantId, Event event) {
        if (!event.acceptsRegistrations()) return 0;
        if (event.getVenueCapacity() != null
                && guestRepo.countActive(event.getId()) >= event.getVenueCapacity()) {
            return 0;   // venue still full — nobody can be placed
        }

        int promoted = 0;
        List<EventWaitlistEntry> waiting =
                waitlistRepo.findWaiting(event.getId(), PageRequest.of(0, PROMOTION_BATCH));

        queue:
        for (EventWaitlistEntry entry : waiting) {
            try {
                GuestRegistrationEngine.Placed placed =
                        engine.register(tenantId, event, commandFor(entry, false));
                entry.promote(placed.guest().getId());
                waitlistRepo.save(entry);
                promoted++;
            } catch (HandyFlowException e) {
                switch (e.getErrorCode()) {
                    case "TIER_SOLD_OUT", "TIER_NOT_ON_SALE" -> { /* skip this entry, try the next */ }
                    case "DUPLICATE_REGISTRATION", "RESOURCE_NOT_FOUND" -> {
                        entry.remove();
                        waitlistRepo.save(entry);
                    }
                    default -> {
                        break queue;
                    }
                }
            }
        }
        if (promoted > 0) {
            log.info("[Events] Promoted {} waitlisted guest(s) for event={}", promoted, event.getEventNumber());
        }
        return promoted;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A waitlisted person was promised a place, so the tier's sale window never
     * blocks their promotion. The registration DEADLINE does for automatic
     * promotion (nobody is added after registration closed without a human
     * deciding to), and is overridable only by manual staff promotion.
     */
    private GuestRegistrationEngine.Command commandFor(EventWaitlistEntry entry, boolean manual) {
        return GuestRegistrationEngine.Command.builder()
                .fullName(entry.getFullName())
                .email(entry.getEmail())
                .phone(entry.getPhone())
                .company(entry.getCompany())
                .dietaryRequirements(entry.getDietaryRequirements())
                .tierId(entry.getTierId())
                .customerId(entry.getCustomerId())
                .source("WAITLIST")
                .ignoreSaleWindow(true)
                .ignoreDeadline(manual)
                .sendTicketEmail(true)
                .audit(true)
                .build();
    }

    private Event lockEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveByIdForUpdate(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private Map<UUID, String> loadTierNames(UUID eventId) {
        return tierRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventTicketTier::getId, EventTicketTier::getName));
    }

    private WaitlistEntryResponse toResponse(EventWaitlistEntry e, String tierName, Integer position) {
        return new WaitlistEntryResponse(e.getId(), e.getFullName(), e.getEmail(), e.getPhone(),
                e.getCompany(), e.getTierId(), tierName, e.getStatus(), position,
                e.getCreatedAt(), e.getPromotedGuestId());
    }

    private void sendJoinedEmail(String email, String fullName, String eventTitle) {
        try {
            emailService.send(email, "You're on the waitlist for " + eventTitle,
                    "<p>Hi " + HtmlUtils.htmlEscape(fullName) + ",</p>"
                            + "<p>You've been added to the waitlist for <strong>"
                            + HtmlUtils.htmlEscape(eventTitle) + "</strong>.</p>"
                            + "<p>If a place opens up we'll register you automatically and email your ticket — "
                            + "there's nothing more you need to do.</p>");
        } catch (Exception e) {
            log.error("[Events] Failed to queue waitlist confirmation email: {}", e.getMessage());
        }
    }
}
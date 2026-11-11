package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventCheckIn;
import za.co.handyflow.platform.events.domain.model.EventGuest;
import za.co.handyflow.platform.events.domain.model.EventTicketTier;
import za.co.handyflow.platform.events.domain.repository.EventCheckInRepository;
import za.co.handyflow.platform.events.domain.repository.EventGuestRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.domain.repository.EventTicketTierRepository;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Group registration, walk-ins and ticket transfers. Every placement goes
 * through GuestRegistrationEngine; this class adds only what is specific to
 * each operation (all-or-nothing groups, instant check-in, seat hand-over).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestOperationsService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository      guestRepo;
    private final EventCheckInRepository    checkInRepo;
    private final GuestRegistrationEngine   engine;
    private final EventNumberGenerator      numberGen;
    private final EmailService              emailService;
    private final EventAuditService         audit;
    private final PaymentLedgerService      payments;

    // ── Group registration ────────────────────────────────────────────────────

    /**
     * Registers a group in one go, ALL OR NOTHING. If any member can't be
     * placed (a duplicate, the tier selling out mid-way) the exception rolls
     * the whole transaction back, including seats already claimed for earlier
     * members — nobody is left with half a group registered.
     * <p>
     * The remaining-seats check up front just turns the common failure
     * ("not enough room") into a precise message instead of a generic
     * sold-out error part-way through.
     */
    @Transactional
    public GroupRegistrationResponse registerGroup(TenantId tenantId, UUID eventId,
                                                   GroupRegistrationRequest req) {
        Event event = lockEvent(tenantId, eventId);
        engine.assertOpen(event, LocalDateTime.now(SAST), false);

        List<GroupMemberRequest> members = req.members();

        // The same person listed twice in one request would only be caught half-way
        // through; reject it up front with a clear message.
        Set<String> seen = new HashSet<>();
        for (GroupMemberRequest m : members) {
            String email = GuestRegistrationEngine.normalizeEmail(m.email());
            if (email != null && !seen.add(email + "|" + m.fullName().trim().toLowerCase())) {
                throw new HandyFlowException(
                        m.fullName().trim() + " (" + email + ") appears more than once in this group",
                        HttpStatus.BAD_REQUEST, "DUPLICATE_IN_GROUP");
            }
        }

        EventTicketTier tier = null;
        if (req.tierId() != null) {
            final UUID tierId = req.tierId();
            tier = tierRepo.findByIdAndEvent(tierId, eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tier", tierId.toString()));
        }

        long remaining = engine.remainingSeats(event, tier);
        if (members.size() > remaining) {
            throw new HandyFlowException(
                    "Only " + remaining + " place" + (remaining == 1 ? "" : "s")
                            + " left — cannot register a group of " + members.size(),
                    HttpStatus.CONFLICT, "GROUP_EXCEEDS_CAPACITY");
        }

        UUID groupRef = UUID.randomUUID();
        List<EventGuest> placed = new ArrayList<>();
        for (GroupMemberRequest m : members) {
            placed.add(engine.register(tenantId, event, GuestRegistrationEngine.Command.builder()
                    .fullName(m.fullName())
                    .email(m.email())
                    .phone(m.phone())
                    .company(m.company())
                    .dietaryRequirements(m.dietaryRequirements())
                    .tierId(req.tierId())
                    .customerId(req.customerId())
                    .amountPaid(req.amountPaidEach())
                    .source("GROUP")
                    .groupRef(groupRef)
                    .sendTicketEmail(true)
                    .audit(false)               // one summary line below instead of N
                    .build()).guest());
        }

        audit.record(tenantId, eventId, "GROUP_REGISTERED", "GUEST", groupRef,
                "Group of " + placed.size() + " registered — tickets "
                        + placed.get(0).getTicketNumber() + " to " + placed.get(placed.size() - 1).getTicketNumber()
                        + (tier != null ? " (" + tier.getName() + ")" : ""));
        log.info("Registered group of {} for event={} groupRef={}", placed.size(), event.getEventNumber(), groupRef);

        Map<UUID, String> tierNames = tier != null ? Map.of(tier.getId(), tier.getName()) : Map.of();
        List<GuestResponse> responses = placed.stream().map(g -> GuestMapper.toResponse(g, tierNames)).toList();
        return new GroupRegistrationResponse(groupRef, responses.size(), responses);
    }

    // ── Walk-in ───────────────────────────────────────────────────────────────

    /**
     * Registers and checks in someone at the door in a single step. Only while
     * the event is LIVE. Because it's a staff action at the door it ignores the
     * registration deadline and the tier's sale window — but NEVER capacity or
     * tier inventory, so a walk-in can't push the event over its limit.
     */
    @Transactional
    public CheckInResponse walkIn(TenantId tenantId, UUID eventId, WalkInRequest req, UUID scannedBy) {
        Event event = lockEvent(tenantId, eventId);
        if (!"LIVE".equals(event.getStatus())) {
            throw new HandyFlowException(
                    "Walk-ins can only be registered while the event is LIVE (currently " + event.getStatus() + ")",
                    HttpStatus.CONFLICT, "EVENT_NOT_LIVE");
        }

        GuestRegistrationEngine.Placed placed = engine.register(tenantId, event,
                GuestRegistrationEngine.Command.builder()
                        .fullName(req.fullName())
                        .email(req.email())
                        .phone(req.phone())
                        .company(req.company())
                        .dietaryRequirements(req.dietaryRequirements())
                        .tierId(req.tierId())
                        .amountPaid(req.amountPaid())
                        .source("WALK_IN")
                        .ignoreDeadline(true)
                        .ignoreSaleWindow(true)
                        .sendTicketEmail(true)
                        .audit(true)
                        .build());

        EventGuest guest = placed.guest();
        guest.checkIn(scannedBy);
        guestRepo.save(guest);
        if (guest.getTierId() != null) {
            tierRepo.markCheckedIn(guest.getTierId(), Instant.now());
        }
        checkInRepo.save(EventCheckIn.create(tenantId, eventId, guest.getId(), scannedBy,
                req.location(), null, "WALK_IN"));

        long totalCheckedIn = guestRepo.countCheckedIn(eventId);
        log.info("Walk-in checked in event={} ticket={}", event.getEventNumber(), guest.getTicketNumber());

        return new CheckInResponse("WALK_IN", guest.getFullName(),
                placed.tier() != null ? placed.tier().getName() : "—",
                guest.getTicketNumber(), guest.getCheckedInAt(), totalCheckedIn);
    }

    // ── Transfer ──────────────────────────────────────────────────────────────

    /**
     * Moves a ticket to a different person. The SEAT does not change hands
     * through the inventory — sold counts stay exactly as they were — but the
     * TICKET does: the old record becomes TRANSFERRED (its QR code now scans as
     * TRANSFERRED_TICKET) and a brand-new record with a new ticket number and a
     * new QR code is created for the new holder. Payment status is carried
     * over, so the new holder doesn't pay again.
     * <p>
     * A fresh record rather than editing the old one in place is what makes
     * the old QR code genuinely dead — the previous holder can't still walk in
     * with a screenshot of their ticket.
     */
    @Transactional
    public GuestResponse transferGuest(TenantId tenantId, UUID eventId, UUID guestId,
                                       TransferGuestRequest req) {
        Event event = lockEvent(tenantId, eventId);
        if ("COMPLETED".equals(event.getStatus()) || "CANCELLED".equals(event.getStatus())) {
            throw new HandyFlowException("Tickets cannot be transferred for a " + event.getStatus() + " event",
                    HttpStatus.CONFLICT, "EVENT_CLOSED");
        }

        EventGuest old = guestRepo.findByIdAndEvent(guestId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", guestId.toString()));
        if (!"REGISTERED".equals(old.getStatus()) && !"CONFIRMED".equals(old.getStatus())) {
            throw new HandyFlowException(
                    "Only active tickets that haven't been used can be transferred (this one is " + old.getStatus() + ")",
                    HttpStatus.CONFLICT, "TICKET_NOT_TRANSFERABLE");
        }

        String newName  = req.fullName().trim();
        String newEmail = GuestRegistrationEngine.normalizeEmail(req.email());
        if (guestRepo.existsActiveDuplicate(eventId, newEmail, newName)) {
            throw new HandyFlowException(newName + " (" + newEmail + ") is already registered for this event",
                    HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION");
        }

        String tierName = old.getTierId() != null
                ? tierRepo.findById(old.getTierId()).map(EventTicketTier::getName).orElse(null)
                : null;
        String oldEmail = old.getEmail();
        String oldName  = old.getFullName();
        String oldTicket = old.getTicketNumber();

        long seq = guestRepo.countAllByEvent(eventId) + 1;
        String newTicket = numberGen.nextTicket(event.getEventNumber(), seq);

        EventGuest fresh = EventGuest.create(tenantId, eventId, old.getTierId(),
                null,                               // customer link belonged to the previous holder
                newName, newEmail, req.phone(), req.company(), req.dietaryRequirements(),
                newTicket, old.getAmountPaid(), event.isFree());
        fresh.withProvenance("TRANSFER", old.getGroupRef(), old.getId());
        fresh.inheritPaymentFrom(old);

        old.markTransferred();
        guestRepo.save(old);
        guestRepo.save(fresh);
        payments.onGuestTransferred(old.getId(), fresh.getId());

        List<String> details = new ArrayList<>();
        details.add("ticket " + oldTicket + " -> " + newTicket);
        if (req.reason() != null && !req.reason().isBlank()) details.add("reason: " + req.reason().trim());
        audit.record(tenantId, eventId, "GUEST_TRANSFERRED", "GUEST", fresh.getId(),
                "Ticket " + oldTicket + " transferred — replacement ticket " + newTicket, details);
        log.info("Transferred ticket {} -> {} event={}", oldTicket, newTicket, event.getEventNumber());

        // Both emails go out only after the transfer is durably committed.
        final String tierNameForEmail = tierName;
        AfterCommit.run(() -> {
            engine.sendTicketEmail(event, fresh, tierNameForEmail);
            if (oldEmail != null) sendTransferredAwayNotice(oldEmail, oldName, oldTicket, event.getTitle());
        });

        Map<UUID, String> tierNames = old.getTierId() != null && tierName != null
                ? Map.of(old.getTierId(), tierName) : Map.of();
        return GuestMapper.toResponse(fresh, tierNames);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Event lockEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveByIdForUpdate(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private void sendTransferredAwayNotice(String email, String name, String ticketNumber, String eventTitle) {
        try {
            emailService.send(email, "Your ticket for " + eventTitle + " has been transferred",
                    "<p>Hi " + HtmlUtils.htmlEscape(name) + ",</p>"
                            + "<p>Ticket <strong>" + HtmlUtils.htmlEscape(ticketNumber) + "</strong> for <strong>"
                            + HtmlUtils.htmlEscape(eventTitle) + "</strong> has been transferred to another person. "
                            + "It is no longer valid for entry.</p>"
                            + "<p>If you didn't expect this, please contact the organiser.</p>");
        } catch (Exception e) {
            log.error("[Events] Failed to queue transfer notice email: {}", e.getMessage());
        }
    }
}
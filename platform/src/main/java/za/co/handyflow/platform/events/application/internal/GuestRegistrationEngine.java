package za.co.handyflow.platform.events.application.internal;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventGuest;
import za.co.handyflow.platform.events.domain.model.EventTicketTier;
import za.co.handyflow.platform.events.domain.repository.EventGuestRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.domain.repository.EventTicketTierRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * The ONE implementation of "place a person at an event". Single
 * registration, group registration, walk-ins, CSV import, waitlist promotion
 * and (indirectly) transfers all go through here, so capacity, duplicate,
 * deadline, sale-window and ticket-numbering rules cannot drift apart
 * between features.
 * <p>
 * CONTRACT FOR CALLERS:
 * <ul>
 *   <li>The caller must already hold the event row lock (findActiveByIdForUpdate)
 *       inside an open transaction — the capacity, duplicate and ticket-number
 *       checks are only race-free under that lock.</li>
 *   <li>This class is deliberately NOT @Transactional. Its rule violations are
 *       thrown as HandyFlowException BEFORE any row is written, so a caller
 *       (CSV import, waitlist promotion) can catch one and carry on with the
 *       next person without poisoning the surrounding transaction. A
 *       @Transactional proxy here would mark that transaction rollback-only
 *       the moment such an exception crossed its boundary.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class GuestRegistrationEngine {

    /** Event dates are stored as LocalDateTime in the event's own timezone (Africa/Johannesburg). */
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository      guestRepo;
    private final EventNumberGenerator      numberGen;
    private final EventTicketPdfService     ticketPdfService;
    private final EmailService              emailService;
    private final EventAuditService         audit;

    /**
     * Everything needed to place one person. Booleans default to false, so each
     * caller states explicitly what it wants rather than inheriting a surprise.
     *
     * @param source           STAFF | GROUP | WALK_IN | IMPORT | WAITLIST
     * @param ignoreDeadline   staff override of the registration deadline (walk-ins, manual promotion)
     * @param ignoreSaleWindow staff override of a tier's sale window (walk-ins, waitlist promotion);
     *                         an INACTIVE tier is never overridable
     * @param sendTicketEmail  email the PDF ticket after commit (only if the person has an email)
     * @param audit            write one GUEST_REGISTERED audit line; bulk callers turn this off
     *                         and write a single summary line instead
     * @param reserve          paid checkout: hold the place as an unpaid RESERVED guest instead of
     *                         issuing a valid ticket. No ticket email is sent — that happens once
     *                         payment is confirmed.
     */
    @Builder
    record Command(
            String fullName,
            String email,
            String phone,
            String company,
            String dietaryRequirements,
            UUID tierId,
            UUID customerId,
            BigDecimal amountPaid,
            String source,
            UUID groupRef,
            boolean ignoreDeadline,
            boolean ignoreSaleWindow,
            boolean sendTicketEmail,
            boolean audit,
            boolean reserve
    ) {}

    /** The saved guest, plus the tier it was placed in (null if no tier). */
    record Placed(EventGuest guest, EventTicketTier tier) {}

    // ── Placement ─────────────────────────────────────────────────────────────

    Placed register(TenantId tenantId, Event event, Command cmd) {
        LocalDateTime now = LocalDateTime.now(SAST);
        assertOpen(event, now, cmd.ignoreDeadline());

        UUID eventId    = event.getId();
        String fullName = cmd.fullName().trim();
        String email    = normalizeEmail(cmd.email());

        if (email != null && guestRepo.existsActiveDuplicate(eventId, email, fullName)) {
            throw new HandyFlowException(
                    fullName + " (" + email + ") is already registered for this event",
                    HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION");
        }

        if (event.getVenueCapacity() != null
                && guestRepo.countActive(eventId) >= event.getVenueCapacity()) {
            throw new HandyFlowException(
                    "This event has reached its venue capacity of " + event.getVenueCapacity(),
                    HttpStatus.CONFLICT, "EVENT_AT_CAPACITY");
        }

        EventTicketTier tier = null;
        if (cmd.tierId() != null) {
            final UUID tierId = cmd.tierId();
            tier = tierRepo.findByIdAndEvent(tierId, eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tier", tierId.toString()));

            boolean onSale = cmd.ignoreSaleWindow() ? tier.isActive() : tier.isOnSale(now);
            if (!onSale) {
                throw new HandyFlowException(
                        "Ticket tier '" + tier.getName() + "' is not currently on sale",
                        HttpStatus.CONFLICT, "TIER_NOT_ON_SALE");
            }
            // The real oversell guard — atomic availability check + increment.
            if (tierRepo.claimSeat(tier.getId(), Instant.now()) == 0) {
                throw new HandyFlowException(
                        "Ticket tier '" + tier.getName() + "' is sold out",
                        HttpStatus.CONFLICT, "TIER_SOLD_OUT");
            }
            if ("PUBLISHED".equals(event.getStatus()) && tierRepo.countAvailableTiers(eventId) == 0) {
                event.markSoldOut();
                eventRepo.save(event);
            }
        }

        // Safe under the event lock: records are never deleted, so total-ever + 1 can't repeat.
        long guestSeq    = guestRepo.countAllByEvent(eventId) + 1;
        String ticketNum = numberGen.nextTicket(event.getEventNumber(), guestSeq);

        String source = cmd.source() != null ? cmd.source() : "STAFF";
        EventGuest guest = EventGuest.create(tenantId, eventId, cmd.tierId(),
                cmd.customerId(), fullName, email, cmd.phone(),
                cmd.company(), cmd.dietaryRequirements(), ticketNum,
                cmd.amountPaid(), event.isFree());
        guest.withProvenance(source, cmd.groupRef(), null);
        if (cmd.reserve()) guest.reserve();
        guestRepo.save(guest);

        if (cmd.audit()) {
            audit.record(tenantId, eventId, cmd.reserve() ? "SEAT_RESERVED" : "GUEST_REGISTERED", "GUEST", guest.getId(),
                    (cmd.reserve() ? "Seat reserved, awaiting payment — ticket " : "Guest registered — ticket ") + ticketNum
                            + (tier != null ? " (" + tier.getName() + ")" : "")
                            + " via " + source);
        }
        log.info("Registered guest event={} ticket={} source={}", event.getEventNumber(), ticketNum, source);

        if (cmd.sendTicketEmail() && !cmd.reserve() && email != null) {
            final String tierName = tier != null ? tier.getName() : null;
            AfterCommit.run(() -> sendTicketEmail(event, guest, tierName));
        }
        return new Placed(guest, tier);
    }

    // ── Gates & capacity ──────────────────────────────────────────────────────

    /** Throws unless the event is currently accepting registrations (status + deadline). */
    void assertOpen(Event event, LocalDateTime now, boolean ignoreDeadline) {
        if (!event.acceptsRegistrations()) {
            throw new HandyFlowException(
                    "Registration is not open — this event is " + event.getStatus()
                            + ("DRAFT".equals(event.getStatus()) ? " (publish it first)" : ""),
                    HttpStatus.CONFLICT, "REGISTRATION_NOT_OPEN");
        }
        if (!ignoreDeadline && event.isRegistrationClosed(now)) {
            throw new HandyFlowException("The registration deadline for this event has passed",
                    HttpStatus.CONFLICT, "REGISTRATION_CLOSED");
        }
    }

    /** True when a registration for this tier (or the event, if tier is null) would be refused for lack of room. */
    boolean isFull(Event event, EventTicketTier tierOrNull) {
        boolean venueFull = event.getVenueCapacity() != null
                && guestRepo.countActive(event.getId()) >= event.getVenueCapacity();
        boolean tierFull = tierOrNull != null && seatsLeftOnTier(tierOrNull) <= 0;
        return venueFull || tierFull;
    }

    /** How many more people could be placed right now: the tighter of venue room and tier room. */
    long remainingSeats(Event event, EventTicketTier tierOrNull) {
        long venueLeft = event.getVenueCapacity() == null
                ? Long.MAX_VALUE
                : Math.max(0, event.getVenueCapacity() - guestRepo.countActive(event.getId()));
        long tierLeft = tierOrNull == null
                ? Long.MAX_VALUE
                : Math.max(0, seatsLeftOnTier(tierOrNull));
        return Math.min(venueLeft, tierLeft);
    }

    private int seatsLeftOnTier(EventTicketTier tier) {
        Integer left = tierRepo.availableSeats(tier.getId());   // fresh from the DB, never a cached entity
        return left == null ? 0 : left;
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    void sendTicketEmail(Event event, EventGuest guest, String tierName) {
        try {
            byte[] ticketPdf = ticketPdfService.generateTicket(event, guest, tierName);
            emailService.sendWithAttachment(
                    guest.getEmail(),
                    "Your ticket for " + event.getTitle(),
                    buildConfirmationHtml(event, guest),
                    "ticket-" + guest.getTicketNumber() + ".pdf",
                    ticketPdf);
            log.info("[Events] Ticket email sent event={} ticket={}", event.getEventNumber(), guest.getTicketNumber());
        } catch (Exception e) {
            log.warn("[Events] Ticket PDF/email failed for guest={}, sending plain confirmation instead: {}",
                    guest.getId(), e.getMessage());
            try {
                emailService.send(guest.getEmail(),
                        "Your registration for " + event.getTitle(),
                        buildConfirmationHtml(event, guest)
                                + "<p>Your ticket number is <strong>"
                                + HtmlUtils.htmlEscape(guest.getTicketNumber())
                                + "</strong> — please have this ready at the door if your PDF ticket didn't arrive.</p>");
            } catch (Exception fallbackFailure) {
                log.error("[Events] Fallback confirmation email also failed for guest={}: {}",
                        guest.getId(), fallbackFailure.getMessage());
            }
        }
    }

    /** Guest name and event title are user-supplied — escaped before going into HTML. */
    private String buildConfirmationHtml(Event event, EventGuest guest) {
        return "<p>Hi " + HtmlUtils.htmlEscape(guest.getFullName()) + ",</p>"
                + "<p>You're registered for <strong>" + HtmlUtils.htmlEscape(event.getTitle()) + "</strong>.</p>"
                + "<p>Your ticket is attached as a PDF — please bring it (printed or on your "
                + "phone) for QR check-in at the door.</p>";
    }

    static String normalizeEmail(String email) {
        if (email == null) return null;
        String trimmed = email.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
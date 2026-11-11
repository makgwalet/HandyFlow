package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.events.domain.model.*;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class EventsService {

    private static final DateTimeFormatter WHEN_FMT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository      guestRepo;
    private final EventVendorRepository     vendorRepo;
    private final EventCheckInRepository    checkInRepo;
    private final EventNumberGenerator      numberGen;
    private final EmailService              emailService;
    private final EventAuditService         audit;
    private final GuestRegistrationEngine   engine;
    private final WaitlistService           waitlist;
    private final PaymentLedgerService      payments;

    // ── Events ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<EventResponse> getEvents(TenantId tenantId, String status,
                                         String type, Pageable pageable) {
        return eventRepo.findAll(tenantId, status, type, pageable)
                .map(this::toEventResponse);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(TenantId tenantId, UUID id) {
        return eventRepo.findActiveById(tenantId, id)
                .map(this::toEventResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id.toString()));
    }

    @Transactional
    public EventResponse createEvent(TenantId tenantId, CreateEventRequest req,
                                     UUID createdBy) {
        String number = numberGen.next(tenantId);
        Event event = Event.create(tenantId, number, req.title(), req.description(),
                req.eventType(), req.venueName(), req.venueAddress(), req.venueCapacity(),
                req.startDatetime(), req.endDatetime(), req.isFree(), req.isPrivate(),
                req.registrationDeadline(), req.notes(), createdBy);
        eventRepo.save(event);
        audit.record(tenantId, event.getId(), "EVENT_CREATED", "EVENT", event.getId(),
                "Event " + number + " created");
        log.info("Created event={} title={} tenant={}", number, req.title(), tenantId);
        return toEventResponse(event);
    }

    /**
     * Edits an event (full replace). Locks the event row so an edit can't
     * interleave with a registration. Raising venue capacity frees room, so
     * the waitlist is promoted straight away.
     */
    @Transactional
    public EventResponse updateEvent(TenantId tenantId, UUID id, UpdateEventRequest req) {
        Event event = findActiveForUpdate(tenantId, id);

        if (req.venueCapacity() != null && req.venueCapacity() < guestRepo.countActive(id)) {
            throw new HandyFlowException(
                    "Venue capacity cannot be lowered below the " + guestRepo.countActive(id)
                            + " guests already registered",
                    HttpStatus.CONFLICT, "CAPACITY_BELOW_REGISTERED");
        }
        if (event.isFree() != req.isFree() && guestRepo.countAllByEvent(id) > 0) {
            throw new HandyFlowException(
                    "Cannot switch between free and paid once guests have registered",
                    HttpStatus.CONFLICT, "PRICING_LOCKED");
        }
        if ("LIVE".equals(event.getStatus())) {
            boolean structural = !Objects.equals(event.getStartDatetime(), req.startDatetime())
                    || !Objects.equals(event.getRegistrationDeadline(), req.registrationDeadline());
            if (structural) {
                throw new HandyFlowException(
                        "A LIVE event's start time and registration deadline can no longer be changed",
                        HttpStatus.CONFLICT, "EVENT_LIVE_LOCKED");
            }
        }

        Integer oldCapacity = event.getVenueCapacity();
        List<String> changes = event.applyUpdate(req.title(), req.description(), req.eventType(),
                req.venueName(), req.venueAddress(), req.venueCapacity(),
                req.startDatetime(), req.endDatetime(), req.isFree(), req.isPrivate(),
                req.registrationDeadline(), req.notes());

        if (!changes.isEmpty()) {
            eventRepo.save(event);
            audit.record(tenantId, id, "EVENT_UPDATED", "EVENT", id,
                    "Event " + event.getEventNumber() + " updated (" + changes.size() + " change"
                            + (changes.size() == 1 ? "" : "s") + ")", changes);
            log.info("Updated event={} changes={}", event.getEventNumber(), changes.size());

            // More room (a higher limit, or no limit at all) may let waiting guests in.
            Integer newCapacity = event.getVenueCapacity();
            if (oldCapacity != null && (newCapacity == null || newCapacity > oldCapacity)) {
                waitlist.promoteEligible(tenantId, event);
            }
        }
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse publishEvent(TenantId tenantId, UUID id) {
        Event event = findActiveForUpdate(tenantId, id);
        event.publish();
        eventRepo.save(event);
        audit.record(tenantId, id, "EVENT_PUBLISHED", "EVENT", id,
                "Event " + event.getEventNumber() + " published — registration opened");
        log.info("Published event={}", event.getEventNumber());
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse goLive(TenantId tenantId, UUID id) {
        Event event = findActiveForUpdate(tenantId, id);
        event.goLive();
        eventRepo.save(event);
        audit.record(tenantId, id, "EVENT_WENT_LIVE", "EVENT", id,
                "Event " + event.getEventNumber() + " is now LIVE — check-in enabled");
        log.info("Event {} is now LIVE", event.getEventNumber());
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse completeEvent(TenantId tenantId, UUID id) {
        Event event = findActiveForUpdate(tenantId, id);
        event.complete();
        eventRepo.save(event);
        waitlist.closeWaitlist(id);
        audit.record(tenantId, id, "EVENT_COMPLETED", "EVENT", id,
                "Event " + event.getEventNumber() + " completed");
        log.info("Completed event={}", event.getEventNumber());
        return toEventResponse(event);
    }

    /** Back-compatible overload: no reason recorded, no guests emailed (the pre-Chunk-2 behaviour). */
    @Transactional
    public EventResponse cancelEvent(TenantId tenantId, UUID id) {
        return cancelEvent(tenantId, id, null);
    }

    /**
     * Cancels an event. Locking the row first means no registration can slip
     * in between the status change and the guest list used for notification.
     * The waitlist is closed — nobody can be promoted into a cancelled event.
     */
    @Transactional
    public EventResponse cancelEvent(TenantId tenantId, UUID id, CancelEventRequest req) {
        Event event = findActiveForUpdate(tenantId, id);
        event.cancel();
        eventRepo.save(event);
        waitlist.closeWaitlist(id);
        int holdsReleased = payments.cancelOpenCheckouts(tenantId, event, "Event cancelled");

        String reason = (req != null && req.reason() != null && !req.reason().isBlank())
                ? req.reason().trim() : null;
        audit.record(tenantId, id, "EVENT_CANCELLED", "EVENT", id,
                "Event " + event.getEventNumber() + " cancelled"
                        + (reason != null ? " — " + reason : " (no reason recorded)")
                        + (holdsReleased > 0 ? "; " + holdsReleased + " open checkout" + (holdsReleased == 1 ? "" : "s") + " cancelled" : ""));

        if (req != null && req.notifyGuests()) {
            String body = "<p>We're sorry to let you know that <strong>"
                    + HtmlUtils.htmlEscape(event.getTitle()) + "</strong>, scheduled for "
                    + formatWhen(event.getStartDatetime()) + ", has been <strong>cancelled</strong>.</p>"
                    + (reason != null ? "<p>Reason: " + HtmlUtils.htmlEscape(reason) + "</p>" : "")
                    + "<p>The organiser will be in touch about any refund or next steps.</p>";
            notifyGuestsAfterCommit(event, "Cancelled: " + event.getTitle(), body);
        }
        log.info("Cancelled event={}", event.getEventNumber());
        return toEventResponse(event);
    }

    /**
     * Moves an event to new dates with a mandatory reason. Deliberately NOT a
     * separate status: a postponed event keeps its lifecycle state, and the
     * reason, old dates and new dates live in the audit trail. Registrations
     * and tickets stay valid; ticket sale windows are NOT shifted.
     */
    @Transactional
    public EventResponse postponeEvent(TenantId tenantId, UUID id, PostponeEventRequest req) {
        Event event = findActiveForUpdate(tenantId, id);
        LocalDateTime oldStart = event.getStartDatetime();

        List<String> changes = event.reschedule(req.newStartDatetime(), req.newEndDatetime());
        if (changes.isEmpty()) {
            throw new HandyFlowException("The new dates are the same as the current dates",
                    HttpStatus.BAD_REQUEST, "NO_DATE_CHANGE");
        }
        eventRepo.save(event);

        String reason = req.reason().trim();
        audit.record(tenantId, id, "EVENT_POSTPONED", "EVENT", id,
                "Event " + event.getEventNumber() + " postponed from " + formatWhen(oldStart)
                        + " to " + formatWhen(event.getStartDatetime()) + " — " + reason, changes);

        if (req.notifyGuests()) {
            String body = "<p><strong>" + HtmlUtils.htmlEscape(event.getTitle())
                    + "</strong> has been rescheduled.</p>"
                    + "<p>Previously: " + formatWhen(oldStart) + "<br/>"
                    + "<strong>New date: " + formatWhen(event.getStartDatetime()) + "</strong></p>"
                    + "<p>Reason: " + HtmlUtils.htmlEscape(reason) + "</p>"
                    + "<p>Your registration and ticket remain valid. If you can no longer attend, "
                    + "please contact the organiser.</p>";
            notifyGuestsAfterCommit(event, "Rescheduled: " + event.getTitle(), body);
        }
        log.info("Postponed event={} to {}", event.getEventNumber(), event.getStartDatetime());
        return toEventResponse(event);
    }

    /**
     * Copies an event into a new DRAFT starting at {@code newStartDatetime}.
     * Duration, registration deadline and ticket sale windows keep their
     * offsets. Copies ticket tiers (sold/checked-in reset to zero); does NOT
     * copy guests, the waitlist, vendors, check-ins or the linked survey.
     */
    @Transactional
    public EventResponse duplicateEvent(TenantId tenantId, UUID id,
                                        DuplicateEventRequest req, UUID createdBy) {
        Event source = findActive(tenantId, id);
        Duration shift = Duration.between(source.getStartDatetime(), req.newStartDatetime());

        String title = (req.title() != null && !req.title().isBlank())
                ? req.title().trim() : "Copy of " + source.getTitle();
        String number = numberGen.next(tenantId);

        Event copy = Event.create(tenantId, number, title, source.getDescription(),
                source.getEventType(), source.getVenueName(), source.getVenueAddress(),
                source.getVenueCapacity(), req.newStartDatetime(),
                source.getEndDatetime().plus(shift), source.isFree(), source.isPrivate(),
                source.getRegistrationDeadline() != null ? source.getRegistrationDeadline().plus(shift) : null,
                source.getNotes(), createdBy);
        eventRepo.save(copy);

        int tiersCopied = 0;
        for (EventTicketTier t : tierRepo.findByEvent(id)) {
            tierRepo.save(EventTicketTier.create(tenantId, copy.getId(), t.getName(), t.getDescription(),
                    t.getPrice(), t.getQuantity(),
                    t.getSaleStart() != null ? t.getSaleStart().plus(shift) : null,
                    t.getSaleEnd() != null ? t.getSaleEnd().plus(shift) : null));
            tiersCopied++;
        }

        audit.record(tenantId, copy.getId(), "EVENT_DUPLICATED", "EVENT", copy.getId(),
                "Event " + number + " created as a copy of " + source.getEventNumber()
                        + " (" + tiersCopied + " ticket tier" + (tiersCopied == 1 ? "" : "s") + " copied)");
        log.info("Duplicated event={} -> {}", source.getEventNumber(), number);
        return toEventResponse(copy);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> getAuditTrail(TenantId tenantId, UUID eventId, Pageable pageable) {
        findActive(tenantId, eventId);
        return audit.getTrail(tenantId, eventId, pageable);
    }

    // ── Ticket tiers ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TierResponse> getTiers(TenantId tenantId, UUID eventId) {
        findActive(tenantId, eventId);
        return tierRepo.findByEvent(eventId).stream().map(this::toTierResponse).toList();
    }

    @Transactional
    public TierResponse createTier(TenantId tenantId, UUID eventId,
                                   CreateTierRequest req) {
        Event event = findActive(tenantId, eventId);
        if ("COMPLETED".equals(event.getStatus()) || "CANCELLED".equals(event.getStatus())) {
            throw new HandyFlowException("Ticket tiers cannot be added to a " + event.getStatus() + " event",
                    HttpStatus.CONFLICT, "EVENT_CLOSED");
        }
        if (req.saleStart() != null && req.saleEnd() != null && !req.saleEnd().isAfter(req.saleStart())) {
            throw new HandyFlowException("The sale window must end after it starts",
                    HttpStatus.BAD_REQUEST, "SALE_WINDOW_INVALID");
        }
        EventTicketTier tier = EventTicketTier.create(tenantId, eventId,
                req.name(), req.description(), req.price(), req.quantity(),
                req.saleStart(), req.saleEnd());
        tierRepo.save(tier);
        audit.record(tenantId, eventId, "TIER_CREATED", "TIER", tier.getId(),
                "Ticket tier '" + tier.getName() + "' created (" + tier.getQuantity()
                        + " tickets at R" + tier.getPrice() + ")");
        return toTierResponse(tier);
    }

    // ── Guests ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<GuestResponse> getGuests(TenantId tenantId, UUID eventId,
                                         String status, UUID tierId,
                                         Pageable pageable) {
        findActive(tenantId, eventId);
        Map<UUID, String> tierNames = loadTierNames(eventId);
        return guestRepo.findByEvent(eventId, status, tierId, pageable)
                .map(g -> GuestMapper.toResponse(g, tierNames));
    }

    /**
     * Registers one guest. The event row lock taken here is what makes the
     * engine's capacity, duplicate and ticket-number checks race-free; all
     * the actual rules live in GuestRegistrationEngine so that group
     * registration, walk-ins, imports and waitlist promotion apply exactly
     * the same ones.
     */
    @Transactional
    public GuestResponse registerGuest(TenantId tenantId, UUID eventId,
                                       RegisterGuestRequest req) {
        Event event = findActiveForUpdate(tenantId, eventId);

        GuestRegistrationEngine.Placed placed = engine.register(tenantId, event,
                GuestRegistrationEngine.Command.builder()
                        .fullName(req.fullName())
                        .email(req.email())
                        .phone(req.phone())
                        .company(req.company())
                        .dietaryRequirements(req.dietaryRequirements())
                        .tierId(req.tierId())
                        .customerId(req.customerId())
                        .amountPaid(req.amountPaid())
                        .source("STAFF")
                        .sendTicketEmail(true)
                        .audit(true)
                        .build());

        Map<UUID, String> tierNames = placed.tier() != null
                ? Map.of(placed.tier().getId(), placed.tier().getName()) : Map.of();
        return GuestMapper.toResponse(placed.guest(), tierNames);
    }

    /**
     * Cancels a registration, gives the seat back, and offers it to the
     * waitlist. Idempotent: cancelling an already-cancelled guest returns it
     * unchanged without releasing a second seat.
     */
    @Transactional
    public GuestResponse cancelGuest(TenantId tenantId, UUID eventId, UUID guestId) {
        Event event = findActiveForUpdate(tenantId, eventId);
        if ("COMPLETED".equals(event.getStatus())) {
            throw new HandyFlowException("Cannot cancel registrations for a COMPLETED event",
                    HttpStatus.CONFLICT, "EVENT_COMPLETED");
        }

        EventGuest guest = guestRepo.findByIdAndEvent(guestId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", guestId.toString()));

        if (!"CANCELLED".equals(guest.getStatus())) {
            if ("CHECKED_IN".equals(guest.getStatus())) {
                throw new HandyFlowException("Cannot cancel a guest who has already checked in",
                        HttpStatus.CONFLICT, "GUEST_ALREADY_CHECKED_IN");
            }
            if ("TRANSFERRED".equals(guest.getStatus())) {
                throw new HandyFlowException(
                        "This ticket was transferred to another person — cancel the new holder's ticket instead",
                        HttpStatus.CONFLICT, "TICKET_TRANSFERRED");
            }
            if ("EXPIRED".equals(guest.getStatus())) {
                throw new HandyFlowException("This reservation has already expired unpaid — there is nothing to cancel",
                        HttpStatus.CONFLICT, "RESERVATION_EXPIRED");
            }
            String statusBefore = guest.getStatus();
            String paymentBefore = guest.getPaymentStatus();
            guest.cancel();
            guestRepo.save(guest);

            // Don't leave a live checkout open for a cancelled reservation, and don't let a paid-for
            // ticket be cancelled with nobody owed anything: both are reflected on the payment record.
            if ("RESERVED".equals(statusBefore)) {
                payments.onReservationCancelled(guest.getId());
            } else if ("PAID".equals(paymentBefore)) {
                payments.onPaidGuestCancelled(guest.getId());
            }

            if (guest.getTierId() != null) {
                tierRepo.releaseSeat(guest.getTierId(), Instant.now());
            }
            if ("SOLD_OUT".equals(event.getStatus()) && tierRepo.countAvailableTiers(eventId) > 0) {
                event.reopenSales();
                eventRepo.save(event);
            }
            audit.record(tenantId, eventId, "GUEST_CANCELLED", "GUEST", guest.getId(),
                    "Registration cancelled — ticket " + guest.getTicketNumber());
            log.info("Cancelled guest ticket={} event={}", guest.getTicketNumber(), event.getEventNumber());

            // The freed seat goes to the next person waiting, under the same event lock.
            waitlist.promoteEligible(tenantId, event);
        }

        return GuestMapper.toResponse(guest, loadTierNames(eventId));
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    /**
     * Validates a scan and records attendance. Every scan — success or not — is
     * written to event_check_ins. The guest row is locked for the duration of
     * the transaction, so two simultaneous scans of one ticket can't both
     * succeed. Only accepted while the event is LIVE.
     * Results: SUCCESS, ALREADY_CHECKED_IN, CANCELLED_TICKET, TRANSFERRED_TICKET, PAYMENT_PENDING,
     * EXPIRED_TICKET, NOT_FOUND.
     */
    @Transactional
    public CheckInResponse checkIn(TenantId tenantId, UUID eventId,
                                   CheckInRequest req, UUID scannedBy) {
        Event event = findActive(tenantId, eventId);
        if (!"LIVE".equals(event.getStatus())) {
            throw new HandyFlowException(
                    "Check-in is only available while the event is LIVE (currently " + event.getStatus() + ")",
                    HttpStatus.CONFLICT, "EVENT_NOT_LIVE");
        }

        String input = req.qrCode() != null ? req.qrCode().trim() : "";
        EventGuest guest = input.isEmpty()
                ? null
                : guestRepo.findForCheckIn(eventId, input).orElse(null);

        String result;
        String guestName = "Unknown";
        String tierName  = "—";
        String ticketNum = "—";

        if (guest == null) {
            result = "NOT_FOUND";
        } else {
            guestName = guest.getFullName();
            ticketNum = guest.getTicketNumber();
            if (guest.getTierId() != null) {
                tierName = tierRepo.findById(guest.getTierId())
                        .map(EventTicketTier::getName).orElse("—");
            }

            if ("RESERVED".equals(guest.getStatus())) {
                result = "PAYMENT_PENDING";         // a held seat, not a ticket — payment hasn't been confirmed
            } else if ("EXPIRED".equals(guest.getStatus())) {
                result = "EXPIRED_TICKET";          // the hold ran out unpaid
            } else if ("CANCELLED".equals(guest.getStatus())) {
                result = "CANCELLED_TICKET";
            } else if ("TRANSFERRED".equals(guest.getStatus())) {
                result = "TRANSFERRED_TICKET";      // the old holder's QR — the ticket now belongs to someone else
            } else if ("CHECKED_IN".equals(guest.getStatus())) {
                result = "ALREADY_CHECKED_IN";
            } else {
                guest.checkIn(scannedBy);
                guestRepo.save(guest);
                if (guest.getTierId() != null) {
                    tierRepo.markCheckedIn(guest.getTierId(), Instant.now());
                }
                result = "SUCCESS";
            }
        }

        checkInRepo.save(EventCheckIn.create(tenantId, eventId,
                guest != null ? guest.getId() : null,
                scannedBy, req.location(), req.scanDevice(), result));

        long totalCheckedIn = guestRepo.countCheckedIn(eventId);
        log.info("Check-in {} event={} guest={} result={}", ticketNum, eventId, guestName, result);

        return new CheckInResponse(result, guestName, tierName,
                ticketNum, guest != null ? guest.getCheckedInAt() : null, totalCheckedIn);
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getStats(TenantId tenantId, UUID eventId) {
        findActive(tenantId, eventId);
        long registered = guestRepo.countActive(eventId);
        long checkedIn  = guestRepo.countCheckedIn(eventId);
        long cancelled  = guestRepo.countByStatus(eventId, "CANCELLED");

        long totalVendors     = vendorRepo.countByEvent(eventId);
        long confirmedVendors = vendorRepo.countConfirmedByEvent(eventId);

        return new EventStatsResponse(registered, checkedIn, cancelled, totalVendors, confirmedVendors);
    }

    // ── Vendors ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VendorResponse> getVendors(TenantId tenantId, UUID eventId) {
        findActive(tenantId, eventId);
        return vendorRepo.findByEvent(eventId).stream().map(this::toVendorResponse).toList();
    }

    @Transactional
    public VendorResponse addVendor(TenantId tenantId, UUID eventId,
                                    AddVendorRequest req) {
        findActive(tenantId, eventId);
        EventVendor vendor = EventVendor.create(tenantId, eventId,
                req.vendorType(), req.companyName(), req.contactName(),
                req.contactPhone(), req.contactEmail(), req.serviceDescription(),
                req.quotedAmount(), req.notes());
        vendorRepo.save(vendor);
        audit.record(tenantId, eventId, "VENDOR_ADDED", "VENDOR", vendor.getId(),
                "Vendor added: " + vendor.getCompanyName() + " (" + vendor.getVendorType() + ")");
        return toVendorResponse(vendor);
    }

    @Transactional
    public VendorResponse confirmVendor(TenantId tenantId, UUID eventId, UUID vendorId) {
        findActive(tenantId, eventId);
        EventVendor vendor = vendorRepo.findByIdAndEvent(vendorId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId.toString()));
        vendor.confirm();
        vendorRepo.save(vendor);
        audit.record(tenantId, eventId, "VENDOR_CONFIRMED", "VENDOR", vendor.getId(),
                "Vendor confirmed: " + vendor.getCompanyName());
        return toVendorResponse(vendor);
    }

    // ── Guest notifications (cancel / postpone) ───────────────────────────────

    /**
     * Snapshots the recipient list NOW (inside the transaction, so it reflects
     * exactly who was registered when the decision was made), then sends once
     * the transaction commits. EmailService.send is @Async on a bounded pool,
     * so a large guest list queues rather than blocking the request thread.
     */
    private void notifyGuestsAfterCommit(Event event, String subject, String bodyHtml) {
        List<GuestContact> recipients = new ArrayList<>();
        for (EventGuest g : guestRepo.findActiveWithEmail(event.getId())) {
            recipients.add(new GuestContact(g.getEmail(), g.getFullName()));
        }
        if (recipients.isEmpty()) return;

        log.info("[Events] Notifying {} guest(s) for event={} subject={}",
                recipients.size(), event.getEventNumber(), subject);
        AfterCommit.run(() -> {
            for (GuestContact r : recipients) {
                try {
                    emailService.send(r.email(), subject,
                            "<p>Hi " + HtmlUtils.htmlEscape(r.name()) + ",</p>" + bodyHtml);
                } catch (Exception e) {
                    log.error("[Events] Failed to queue notification email for event={}: {}",
                            event.getEventNumber(), e.getMessage());
                }
            }
        });
    }

    private record GuestContact(String email, String name) {}

    // ── Private helpers ───────────────────────────────────────────────────────

    private Event findActive(TenantId tenantId, UUID id) {
        return eventRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id.toString()));
    }

    /** Locks the event row until the transaction ends. */
    private Event findActiveForUpdate(TenantId tenantId, UUID id) {
        return eventRepo.findActiveByIdForUpdate(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id.toString()));
    }

    private Map<UUID, String> loadTierNames(UUID eventId) {
        return tierRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventTicketTier::getId, EventTicketTier::getName));
    }

    private String formatWhen(LocalDateTime dt) {
        return dt == null ? "(date to be confirmed)" : dt.format(WHEN_FMT);
    }

    private EventResponse toEventResponse(Event e) {
        return new EventResponse(e.getId(), e.getEventNumber(), e.getTitle(),
                e.getDescription(), e.getEventType(), e.getStatus(), e.getVenueName(),
                e.getVenueAddress(), e.getVenueCapacity(), e.getStartDatetime(),
                e.getEndDatetime(), e.isFree(), e.isPrivate(),
                e.getRegistrationDeadline(), e.getSurveyId(), e.getNotes(), e.getCreatedAt());
    }

    private TierResponse toTierResponse(EventTicketTier t) {
        return new TierResponse(t.getId(), t.getName(), t.getDescription(),
                t.getPrice(), t.getQuantity(), t.getQuantitySold(),
                t.getQuantityCheckedIn(), t.getAvailable(),
                t.getSaleStart(), t.getSaleEnd(), t.isActive());
    }

    private VendorResponse toVendorResponse(EventVendor v) {
        return new VendorResponse(v.getId(), v.getVendorType(), v.getCompanyName(),
                v.getContactName(), v.getContactPhone(), v.getContactEmail(),
                v.getServiceDescription(), v.getQuotedAmount(), v.isConfirmed(),
                v.getNotes(), v.getCreatedAt());
    }
}
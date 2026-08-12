package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.*;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class EventsService {

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository      guestRepo;
    private final EventVendorRepository     vendorRepo;
    private final EventCheckInRepository    checkInRepo;
    private final EventNumberGenerator      numberGen;
    private final EventTicketPdfService ticketPdfService;
    private final EmailService emailService;

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
        log.info("Created event={} title={} tenant={}", number, req.title(), tenantId);
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse publishEvent(TenantId tenantId, UUID id) {
        Event event = findActive(tenantId, id);
        event.publish();
        eventRepo.save(event);
        log.info("Published event={}", event.getEventNumber());
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse goLive(TenantId tenantId, UUID id) {
        Event event = findActive(tenantId, id);
        event.goLive();
        eventRepo.save(event);
        log.info("Event {} is now LIVE", event.getEventNumber());
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse completeEvent(TenantId tenantId, UUID id) {
        Event event = findActive(tenantId, id);
        event.complete();
        eventRepo.save(event);
        log.info("Completed event={}", event.getEventNumber());
        return toEventResponse(event);
    }

    @Transactional
    public EventResponse cancelEvent(TenantId tenantId, UUID id) {
        Event event = findActive(tenantId, id);
        event.cancel();
        eventRepo.save(event);
        log.info("Cancelled event={}", event.getEventNumber());
        return toEventResponse(event);
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
        findActive(tenantId, eventId);
        EventTicketTier tier = EventTicketTier.create(tenantId, eventId,
                req.name(), req.description(), req.price(), req.quantity(),
                req.saleStart(), req.saleEnd());
        tierRepo.save(tier);
        return toTierResponse(tier);
    }

    // ── Guests ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<GuestResponse> getGuests(TenantId tenantId, UUID eventId,
                                         String status, UUID tierId,
                                         Pageable pageable) {
        findActive(tenantId, eventId);
        // Pre-load all tiers for this event once — avoids N+1 inside toGuestResponse
        Map<UUID, String> tierNames = tierRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventTicketTier::getId, EventTicketTier::getName));
        return guestRepo.findByEvent(eventId, status, tierId, pageable)
                .map(g -> toGuestResponseWithTierMap(g, tierNames));
    }

    @Transactional
    public GuestResponse registerGuest(TenantId tenantId, UUID eventId,
                                       RegisterGuestRequest req) {
        Event event = findActive(tenantId, eventId);
        if (List.of("CANCELLED","COMPLETED").contains(event.getStatus()))
            throw new IllegalStateException("Cannot register guests for a " + event.getStatus() + " event");

        if (req.tierId() != null) {
            EventTicketTier tier = tierRepo.findByIdAndEvent(req.tierId(), eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tier", req.tierId().toString()));
            if (!tier.isAvailable())
                throw new IllegalStateException("Ticket tier '" + tier.getName() + "' is sold out");
            tier.incrementSold();
            tierRepo.save(tier);

            if (tierRepo.countAvailableTiers(eventId) == 0 && "PUBLISHED".equals(event.getStatus())) {
                event.markSoldOut();
                eventRepo.save(event);
            }
        }

        long guestSeq    = guestRepo.countActive(eventId) + 1;
        String ticketNum = numberGen.nextTicket(event.getEventNumber(), guestSeq);

        EventGuest guest = EventGuest.create(tenantId, eventId, req.tierId(),
                req.customerId(), req.fullName(), req.email(), req.phone(),
                req.company(), req.dietaryRequirements(), ticketNum,
                req.amountPaid(), event.isFree());
        guestRepo.save(guest);
        log.info("Registered guest={} event={} ticket={}", req.fullName(), event.getEventNumber(), ticketNum);

        // FIX: moved this computation up from after the email-send block
        // (where it was previously declared) to here, BEFORE it's used —
        // the earlier version called sendTicketEmail(..., tierNames.get(...))
        // three lines before tierNames was ever declared, which Java
        // doesn't allow (no forward references to local variables). Same
        // map, computed once, now feeds both the email send below AND the
        // final return — no duplicate query introduced.
        Map<UUID, String> tierNames = tierRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventTicketTier::getId, EventTicketTier::getName));

        if (guest.getEmail() != null && !guest.getEmail().isBlank()) {
            sendTicketEmail(event, guest, tierNames.get(guest.getTierId()));
        }

        return toGuestResponseWithTierMap(guest, tierNames);
    }

    @Transactional
    public GuestResponse cancelGuest(TenantId tenantId, UUID eventId, UUID guestId) {
        findActive(tenantId, eventId);
        EventGuest guest = guestRepo.findByIdAndEvent(guestId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", guestId.toString()));
        guest.cancel();
        guestRepo.save(guest);
        Map<UUID, String> tierNames = tierRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventTicketTier::getId, EventTicketTier::getName));
        return toGuestResponseWithTierMap(guest, tierNames);
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    @Transactional
    public CheckInResponse checkIn(TenantId tenantId, UUID eventId,
                                   CheckInRequest req, UUID scannedBy) {
        findActive(tenantId, eventId);
        // Try QR token first; fall back to ticket number (e.g. EVT-2026-00001-0001)
        // so door staff can type ticket numbers when scanning isn't available.
        String input = req.qrCode() != null ? req.qrCode().trim() : "";
        EventGuest guest = guestRepo.findByQrCode(input)
                .or(() -> guestRepo.findByTicketNumber(input))
                .orElse(null);

        String result;
        String guestName = "Unknown";
        String tierName  = "—";
        String ticketNum = "—";

        if (guest == null || !guest.getEventId().equals(eventId)) {
            result = "NOT_FOUND";
        } else if ("CANCELLED".equals(guest.getStatus())) {
            result    = "CANCELLED_TICKET";
            guestName = guest.getFullName();
            ticketNum = guest.getTicketNumber();
        } else if ("CHECKED_IN".equals(guest.getStatus())) {
            result    = "ALREADY_CHECKED_IN";
            guestName = guest.getFullName();
            ticketNum = guest.getTicketNumber();
            // Look up tier name once
            if (guest.getTierId() != null)
                tierName = tierRepo.findById(guest.getTierId()).map(EventTicketTier::getName).orElse("—");
        } else {
            result    = "SUCCESS";
            guestName = guest.getFullName();
            ticketNum = guest.getTicketNumber();
            guest.checkIn(scannedBy);
            guestRepo.save(guest);

            if (guest.getTierId() != null) {
                tierRepo.findById(guest.getTierId()).ifPresent(tier -> {
                    tier.incrementCheckedIn();
                    tierRepo.save(tier);
                    // capture tierName inside closure
                });
                tierName = tierRepo.findById(guest.getTierId())
                        .map(EventTicketTier::getName).orElse("—");
            }
        }

        EventCheckIn checkIn = EventCheckIn.create(tenantId, eventId,
                guest != null ? guest.getId() : null,
                scannedBy, req.location(), result);
        checkInRepo.save(checkIn);

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

        // FIX: was calling vendorRepo.findByEvent twice — now two separate count queries
        long totalVendors    = vendorRepo.countByEvent(eventId);
        long confirmedVendors = vendorRepo.countConfirmedByEvent(eventId);

        return new EventStatsResponse(registered, checkedIn, 0, totalVendors, confirmedVendors);
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
        return toVendorResponse(vendor);
    }

    @Transactional
    public VendorResponse confirmVendor(TenantId tenantId, UUID eventId, UUID vendorId) {
        findActive(tenantId, eventId);
        EventVendor vendor = vendorRepo.findByIdAndEvent(vendorId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId.toString()));
        vendor.confirm();
        vendorRepo.save(vendor);
        return toVendorResponse(vendor);
    }

    private void sendTicketEmail(Event event, EventGuest guest, String tierName) {
        try {
            byte[] ticketPdf = ticketPdfService.generateTicket(event, guest, tierName);
            // FIX: was 6 args including a trailing "application/pdf" —
            // the real EmailService.sendWithAttachment signature is
            // (String, String, String, String, byte[]), confirmed by the
            // actual compiler error, not guessed.
            emailService.sendWithAttachment(
                    guest.getEmail(),
                    "Your ticket for " + event.getTitle(),
                    buildConfirmationHtml(event, guest),
                    "ticket-" + guest.getTicketNumber() + ".pdf",
                    ticketPdf);
            log.info("[Events] Ticket email sent guest={} event={} ticket={}",
                    guest.getFullName(), event.getEventNumber(), guest.getTicketNumber());
        } catch (Exception e) {
            // PDF generation or send failed — fall back to a plain
            // confirmation with no attachment rather than leaving the
            // guest with nothing at all. Same "never let a PDF problem
            // silently swallow the whole notification" reasoning already
            // established elsewhere in this codebase.
            log.warn("[Events] Ticket PDF/email failed for guest={}, sending plain confirmation instead: {}",
                    guest.getId(), e.getMessage());
            try {
                emailService.send(guest.getEmail(),
                        "Your registration for " + event.getTitle(),
                        buildConfirmationHtml(event, guest)
                                + "<p>Your ticket number is <strong>" + guest.getTicketNumber()
                                + "</strong> — please have this ready at the door if your PDF ticket didn't arrive.</p>");
            } catch (Exception fallbackFailure) {
                log.error("[Events] Fallback confirmation email also failed for guest={}: {}",
                        guest.getId(), fallbackFailure.getMessage());
            }
        }
    }

    private String buildConfirmationHtml(Event event, EventGuest guest) {
        return "<p>Hi " + guest.getFullName() + ",</p>"
                + "<p>You're registered for <strong>" + event.getTitle() + "</strong>.</p>"
                + "<p>Your ticket is attached as a PDF — please bring it (printed or on your "
                + "phone) for QR check-in at the door.</p>";
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Event findActive(TenantId tenantId, UUID id) {
        return eventRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id.toString()));
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

    /**
     * Tier name resolved from a pre-loaded map — no extra DB query per guest.
     * Fixes the N+1 problem in the original toGuestResponse().
     */
    private GuestResponse toGuestResponseWithTierMap(EventGuest g, Map<UUID, String> tierNames) {
        String tierName = g.getTierId() != null ? tierNames.getOrDefault(g.getTierId(), "—") : "—";
        return new GuestResponse(g.getId(), g.getTicketNumber(), g.getQrCode(),
                g.getFullName(), g.getEmail(), g.getPhone(), g.getCompany(),
                g.getDietaryRequirements(), g.getTierId(), tierName,
                g.getStatus(), g.getPaymentStatus(), g.getAmountPaid(),
                g.getCheckedInAt(), g.getCreatedAt());
    }

    private VendorResponse toVendorResponse(EventVendor v) {
        return new VendorResponse(v.getId(), v.getVendorType(), v.getCompanyName(),
                v.getContactName(), v.getContactPhone(), v.getContactEmail(),
                v.getServiceDescription(), v.getQuotedAmount(), v.isConfirmed(),
                v.getNotes(), v.getCreatedAt());
    }
}
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
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventsService {

    private final EventRepository          eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final EventGuestRepository     guestRepo;
    private final EventVendorRepository    vendorRepo;
    private final EventCheckInRepository   checkInRepo;
    private final EventNumberGenerator     numberGen;

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
        log.info("Completed event={} checkedIn={}",
                event.getEventNumber(),
                guestRepo.countCheckedIn(id));
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
        return guestRepo.findByEvent(eventId, status, tierId, pageable)
                .map(g -> toGuestResponse(g, eventId));
    }

    @Transactional
    public GuestResponse registerGuest(TenantId tenantId, UUID eventId,
                                       RegisterGuestRequest req) {
        Event event = findActive(tenantId, eventId);
        if (java.util.List.of("CANCELLED","COMPLETED").contains(event.getStatus()))
            throw new IllegalStateException("Cannot register guests for a " + event.getStatus() + " event");

        // Check tier availability
        if (req.tierId() != null) {
            EventTicketTier tier = tierRepo.findByIdAndEvent(req.tierId(), eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tier", req.tierId().toString()));
            if (!tier.isAvailable())
                throw new IllegalStateException("Ticket tier '" + tier.getName() + "' is sold out");
            tier.incrementSold();
            tierRepo.save(tier);

            // Auto-mark event as sold out if no tiers remain
            if (tierRepo.countAvailableTiers(eventId) == 0 && "PUBLISHED".equals(event.getStatus())) {
                event.markSoldOut();
                eventRepo.save(event);
            }
        }

        long guestSeq = guestRepo.countActive(eventId) + 1;
        String ticketNumber = numberGen.nextTicket(event.getEventNumber(), guestSeq);

        EventGuest guest = EventGuest.create(tenantId, eventId, req.tierId(),
                req.customerId(), req.fullName(), req.email(), req.phone(),
                req.company(), req.dietaryRequirements(), ticketNumber,
                req.amountPaid(), event.isFree());
        guestRepo.save(guest);
        log.info("Registered guest={} event={} ticket={}",
                req.fullName(), event.getEventNumber(), ticketNumber);
        return toGuestResponse(guest, eventId);
    }

    @Transactional
    public GuestResponse cancelGuest(TenantId tenantId, UUID eventId, UUID guestId) {
        findActive(tenantId, eventId);
        EventGuest guest = guestRepo.findByIdAndEvent(guestId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", guestId.toString()));
        guest.cancel();
        guestRepo.save(guest);
        return toGuestResponse(guest, eventId);
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    @Transactional
    public CheckInResponse checkIn(TenantId tenantId, UUID eventId,
                                   CheckInRequest req, UUID scannedBy) {
        findActive(tenantId, eventId);

        // Look up guest by QR code
        EventGuest guest = guestRepo.findByQrCode(req.qrCode()).orElse(null);

        String result;
        String guestName  = "Unknown";
        String tierName   = "—";
        String ticketNum  = "—";

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
        } else {
            result    = "SUCCESS";
            guestName = guest.getFullName();
            ticketNum = guest.getTicketNumber();

            guest.checkIn(scannedBy);
            guestRepo.save(guest);

            // Increment tier checked-in count
            if (guest.getTierId() != null) {
                tierRepo.findById(guest.getTierId()).ifPresent(tier -> {
                    tier.incrementCheckedIn();
                    tierRepo.save(tier);
                });
            }

            if (guest.getTierId() != null) {
                tierName = tierRepo.findById(guest.getTierId())
                        .map(EventTicketTier::getName).orElse("—");
            }
        }

        // Always record the scan attempt
        EventCheckIn checkIn = EventCheckIn.create(tenantId, eventId,
                guest != null ? guest.getId() : null,
                scannedBy, req.location(), result);
        checkInRepo.save(checkIn);

        long totalCheckedIn = guestRepo.countCheckedIn(eventId);
        log.info("Check-in {} event={} guest={} result={}",
                ticketNum, eventId, guestName, result);

        return new CheckInResponse(result, guestName, tierName,
                ticketNum, guest != null ? guest.getCheckedInAt() : null, totalCheckedIn);
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getStats(TenantId tenantId, UUID eventId) {
        findActive(tenantId, eventId);
        long registered  = guestRepo.countActive(eventId);
        long checkedIn   = guestRepo.countCheckedIn(eventId);
        long vendors     = vendorRepo.findByEvent(eventId).size();
        long confirmed   = vendorRepo.findByEvent(eventId).stream()
                .filter(EventVendor::isConfirmed).count();
        return new EventStatsResponse(registered, checkedIn, 0, vendors, confirmed);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Event findActive(TenantId tenantId, UUID id) {
        return eventRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id.toString()));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

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

    private GuestResponse toGuestResponse(EventGuest g, UUID eventId) {
        String tierName = g.getTierId() != null
                ? tierRepo.findById(g.getTierId()).map(EventTicketTier::getName).orElse("—")
                : "—";
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
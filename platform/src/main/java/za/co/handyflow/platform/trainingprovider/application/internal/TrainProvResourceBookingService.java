package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.ResourceBookingRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.TimetableRules;
import za.co.handyflow.platform.trainingprovider.dto.ResourceBookingDtos.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Booking site resources into timetable slots. A non-shareable resource cannot be booked beyond the quantity the site holds at overlapping
 * times unless an administrator approves it with a written reason, which is kept on the booking. Bookings are released with a reason, never
 * deleted, and a cancelled slot frees its resources. Booking says nothing about whether a resource satisfies any standard.
 *
 * Before checking, the request waits for any other booking in progress for the same resource, so two requests cannot both pass.
 */
@Service
@RequiredArgsConstructor
public class TrainProvResourceBookingService {

    private final TrainProvSlotResourceRepository bookings;
    private final TrainProvTimetableSlotRepository slots;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortEventRepository events;
    private final TrainProvSiteResourceRepository resources;
    private final TrainProvVenueRepository venues;

    public List<BookingView> forSlot(TenantId t, UUID slotId) {
        TrainProvTimetableSlot s = slot(t, slotId);
        return bookings.forSlot(t, slotId).stream().<BookingView>map(b -> view(t, b, s)).toList();
    }

    @Transactional
    public CheckResult check(TenantId t, UUID slotId, BookRequest r) {
        TrainProvTimetableSlot s = slot(t, slotId);
        Plan p = plan(t, s, r);
        return new CheckResult(p.clash() == null, p.clash());
    }

    @Transactional
    public BookingView book(TenantId t, UUID slotId, BookRequest r, UUID by, boolean mayApprove) {
        TrainProvTimetableSlot s = slot(t, slotId);
        Plan p = plan(t, s, r);
        if (p.clash() != null) {
            TimetableRules.Clash k = new TimetableRules.Clash(ResourceBookingRules.RESOURCE, p.resource().getId(), p.resource().getName(), s.getId(), s.getCohortId(), p.clash());
            String problem = TimetableRules.overrideProblem(List.of(k), r.overrideReason(), mayApprove);
            if (problem != null) {
                boolean denied = TimetableRules.reasonOk(r.overrideReason()) && !mayApprove;
                throw new HandyFlowException(problem, denied ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT, denied ? "TP_BOOKING_OVERRIDE_DENIED" : "TP_BOOKING_CLASH");
            }
        }
        TrainProvSlotResource b = guard(() -> TrainProvSlotResource.book(t, s.getId(), p.resource().getId(), p.quantity(), p.clash(), r.overrideReason(), by));
        bookings.save(b);
        events.save(TrainProvCohortEvent.of(t, s.getCohortId(), "RESOURCE_BOOKED", p.quantity() + " x " + p.resource().getName() + " for " + s.getTitle() + " on " + s.getSlotDate()
                + (p.clash() == null ? "" : " booked over a clash with approval: " + r.overrideReason().trim()), by));
        return view(t, b, s);
    }

    @Transactional
    public BookingView release(TenantId t, UUID bookingId, String reason, UUID by) {
        TrainProvSlotResource b = bookings.findForTenant(t, bookingId).orElseThrow(() -> new ResourceNotFoundException("TrainProvSlotResource", bookingId.toString()));
        TrainProvTimetableSlot s = slot(t, b.getSlotId());
        guard(() -> { b.release(reason, by); return null; });
        bookings.save(b);
        String name = resources.findForTenant(t, b.getResourceId()).map(TrainProvSiteResource::getName).orElse("Resource");
        events.save(TrainProvCohortEvent.of(t, s.getCohortId(), "RESOURCE_RELEASED", name + " released from " + s.getTitle() + " on " + s.getSlotDate() + ": " + reason.trim(), by));
        return view(t, b, s);
    }

    /** What a resource is booked for from the given moment on. */
    public ResourceSchedule schedule(TenantId t, UUID resourceId, Instant from) {
        TrainProvSiteResource res = resource(t, resourceId);
        List<UpcomingView> rows = bookings.upcomingForResource(t, resourceId, from).stream().map(row -> {
            TrainProvSlotResource b = (TrainProvSlotResource) row[0];
            TrainProvTimetableSlot s = (TrainProvTimetableSlot) row[1];
            String cohortName = cohorts.findForTenant(t, s.getCohortId()).map(TrainProvCohort::getName).orElse("");
            return new UpcomingView(b.getId(), s.getId(), s.getCohortId(), cohortName, s.getTitle(), s.getSlotDate(), s.getStartTime(), s.getEndTime(), b.getQuantity(), b.getOverrideReason() != null);
        }).toList();
        return new ResourceSchedule(res.getId(), res.getQuantity(), res.isShareable(), rows);
    }

    // ---- checks ----

    private record Plan(TrainProvSiteResource resource, int quantity, String clash) {
    }

    private Plan plan(TenantId t, TrainProvTimetableSlot s, BookRequest r) {
        TrainProvCohort c = cohorts.findForTenant(t, s.getCohortId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", s.getCohortId().toString()));
        if (!CohortRules.open(c.getStatus())) {
            throw new HandyFlowException("Resources of a " + c.getStatus().toLowerCase() + " cohort cannot be changed", HttpStatus.CONFLICT, "TP_BOOKING_STATE");
        }
        TrainProvSiteResource res = resource(t, r.resourceId());
        int qty = r.quantity() == null ? 1 : r.quantity();
        refuse(ResourceBookingRules.bookProblem(res.getStatus(), res.getSiteId(), c.getSiteId(), s.getStatus(), qty, res.getQuantity()));
        bookings.lock("trainprov-resource:" + t.getValue() + ":" + res.getId());
        boolean already = bookings.forSlot(t, s.getId()).stream().anyMatch(b -> ResourceBookingRules.BOOKED.equals(b.getStatus()) && b.getResourceId().equals(res.getId()));
        if (already) {
            throw new HandyFlowException("That resource is already booked for this slot. Release it first to change the quantity", HttpStatus.CONFLICT, "TP_BOOKING_STATE");
        }
        int booked = bookedElsewhere(t, res.getId(), s);
        return new Plan(res, qty, ResourceBookingRules.clash(res.getName(), res.isShareable(), res.getQuantity(), booked, qty));
    }

    private int bookedElsewhere(TenantId t, UUID resourceId, TrainProvTimetableSlot s) {
        int sum = 0;
        for (Object[] row : bookings.overlapping(t, resourceId, s.getId(), s.getStartsAt(), s.getEndsAt())) {
            sum += ((TrainProvSlotResource) row[0]).getQuantity();
        }
        return sum;
    }

    private static void refuse(String problem) {
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_BOOKING_STATE");
        }
    }

    // ---- views and lookups ----

    private BookingView view(TenantId t, TrainProvSlotResource b, TrainProvTimetableSlot s) {
        TrainProvSiteResource res = resources.findForTenant(t, b.getResourceId()).orElse(null);
        String clashNow = null;
        String venueNote = null;
        if (res != null && ResourceBookingRules.BOOKED.equals(b.getStatus()) && "SCHEDULED".equals(s.getStatus())) {
            if (b.getOverrideReason() == null) {
                clashNow = ResourceBookingRules.clash(res.getName(), res.isShareable(), res.getQuantity(), bookedElsewhere(t, res.getId(), s), b.getQuantity());
            }
            if (res.getVenueId() != null && s.getVenueId() != null && !res.getVenueId().equals(s.getVenueId())) {
                String kept = venues.findForTenant(t, res.getVenueId()).map(TrainProvVenue::getName).orElse("another venue");
                String used = venues.findForTenant(t, s.getVenueId()).map(TrainProvVenue::getName).orElse("another venue");
                venueNote = "Kept in " + kept + " but the slot is in " + used + ". Check it can be moved";
            }
        }
        return new BookingView(b.getId(), b.getSlotId(), b.getResourceId(), res == null ? null : res.getResourceNo(), res == null ? null : res.getName(),
                res == null ? null : res.getResourceType(), res == null ? null : res.getStatus(), b.getQuantity(), b.getStatus(), b.getClashNote(), b.getOverrideReason(),
                b.getOverrideAt(), b.getReleasedReason(), b.getReleasedAt(), s.getStatus(), clashNow != null, clashNow, venueNote, b.getCreatedAt());
    }

    private TrainProvTimetableSlot slot(TenantId t, UUID id) {
        return slots.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvTimetableSlot", id.toString()));
    }

    private TrainProvSiteResource resource(TenantId t, UUID id) {
        return resources.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvSiteResource", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_BOOKING_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_BOOKING_INVALID");
        }
    }
}

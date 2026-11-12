package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookings.application.BookingsFacade;
import za.co.handyflow.platform.bookings.application.BookingsFacade.BookingInfo;
import za.co.handyflow.platform.bookings.application.BookingsFacade.ServiceInfo;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopOffering;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopReservation;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopOfferingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopReservationRepository;
import za.co.handyflow.platform.fuel.dto.TruckStopDtos.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Truck Stop & Driver Services. Bookings owns the reservation (time, resource, status, price, confirmation emails); Fuel
 * owns which services the truck stop sells and which truck and driver a reservation is for. Division access is enforced by
 * the Fuel division interceptor on /api/v1/fuel/truck-stop. Nothing here posts an invoice or takes payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelTruckStopService {

    private final FuelTruckStopOfferingRepository offerings;
    private final FuelTruckStopReservationRepository reservations;
    private final FuelDepotRepository depots;
    private final BookingsFacade bookings;

    // ---- setup ----

    @Transactional(readOnly = true)
    public List<OfferingResponse> listOfferings(TenantId tenantId) {
        Map<UUID, ServiceInfo> services = servicesById(tenantId);
        return offerings.findAllForTenant(tenantId).stream().map(o -> toResponse(tenantId, o, services.get(o.getBookingsServiceId()))).toList();
    }

    /** Active Bookings services that are not offered yet. */
    @Transactional(readOnly = true)
    public List<ServiceOption> availableServices(TenantId tenantId) {
        Set<UUID> taken = offerings.findAllForTenant(tenantId).stream().map(FuelTruckStopOffering::getBookingsServiceId).collect(Collectors.toSet());
        return bookings.services(tenantId).stream().filter(s -> s.active() && !taken.contains(s.id()))
                .map(s -> new ServiceOption(s.id(), s.name(), s.durationMinutes(), s.price())).toList();
    }

    @Transactional
    public OfferingResponse addOffering(TenantId tenantId, OfferingRequest req, UUID by) {
        ServiceInfo svc = servicesById(tenantId).get(req.bookingsServiceId());
        if (svc == null || !svc.active()) throw new ResourceNotFoundException("Bookings service", String.valueOf(req.bookingsServiceId()));
        if (offerings.serviceTaken(tenantId, req.bookingsServiceId())) throw new ConflictException(svc.name() + " is already a truck stop service.");
        if (req.depotId() != null && depots.findActiveById(tenantId, req.depotId()).isEmpty()) throw new ResourceNotFoundException("Depot", req.depotId().toString());
        FuelTruckStopOffering o = FuelTruckStopOffering.create(tenantId, req.bookingsServiceId(), req.kind(), req.depotId(), by);
        offerings.save(o);
        return toResponse(tenantId, o, svc);
    }

    /** Switching off stops new reservations; reservations already made are left alone in Bookings. */
    @Transactional
    public OfferingResponse setActive(TenantId tenantId, UUID id, boolean active) {
        FuelTruckStopOffering o = findOffering(tenantId, id);
        o.setActive(active);
        offerings.save(o);
        return toResponse(tenantId, o, servicesById(tenantId).get(o.getBookingsServiceId()));
    }

    // ---- reservations ----

    @Transactional(readOnly = true)
    public List<Slot> slots(TenantId tenantId, UUID offeringId, LocalDate date, UUID resourceId) {
        FuelTruckStopOffering o = findOffering(tenantId, offeringId);
        return bookings.slots(tenantId, o.getBookingsServiceId(), date, resourceId).stream().map(s -> new Slot(s.start(), s.end(), s.label())).toList();
    }

    @Transactional
    public ReservationResponse reserve(TenantId tenantId, ReserveRequest req, UUID by) {
        FuelTruckStopOffering o = findOffering(tenantId, req.offeringId());
        if (!o.isActive()) throw new ConflictException("This service is switched off, so it cannot be reserved.");
        FuelTruckStopReservation probe = FuelTruckStopReservation.create(tenantId, o.getId(), UUID.randomUUID(), req.vehicleRegistration(), req.driverName(), req.driverPhone(), by); // validates the truck details before Bookings is touched
        // A service with bays/resources must be booked against one of them, otherwise Bookings cannot stop a double booking.
        var resources = bookings.resources(tenantId, o.getBookingsServiceId());
        if (!resources.isEmpty()) {
            if (req.resourceId() == null) throw new IllegalArgumentException("Choose which bay or station to reserve.");
            if (resources.stream().noneMatch(x -> x.id().equals(req.resourceId()))) throw new IllegalArgumentException("That bay or station does not offer this service.");
        }
        // Same transaction as Bookings: if saving ours fails, the booking rolls back with it.
        BookingInfo b = bookings.reserve(tenantId, o.getBookingsServiceId(), req.resourceId(), req.date(), req.start(),
                (probe.getDriverName() != null ? probe.getDriverName() : probe.getVehicleRegistration()) + " (" + probe.getVehicleRegistration() + ")",
                probe.getDriverPhone(), null, notes(req, probe));
        FuelTruckStopReservation r = FuelTruckStopReservation.create(tenantId, o.getId(), b.id(), req.vehicleRegistration(), req.driverName(), req.driverPhone(), by);
        reservations.save(r);
        log.info("Truck stop reservation {} vehicle={} kind={} by={}", b.bookingNumber(), r.getVehicleRegistration(), o.getKind(), by);
        return toReservation(r, o, b);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> recent(TenantId tenantId, String vehicle, int limit) {
        int n = Math.max(1, Math.min(limit, 100));
        List<FuelTruckStopReservation> rows = vehicle == null || vehicle.isBlank()
                ? reservations.findRecent(tenantId, PageRequest.of(0, n))
                : reservations.findByVehicle(tenantId, vehicle.trim(), PageRequest.of(0, n));
        Map<UUID, FuelTruckStopOffering> byId = offerings.findAllForTenant(tenantId).stream().collect(Collectors.toMap(FuelTruckStopOffering::getId, Function.identity()));
        List<ReservationResponse> out = new ArrayList<>();
        for (FuelTruckStopReservation r : rows) {
            BookingInfo b;
            try { b = bookings.get(tenantId, r.getBookingId()); } catch (ResourceNotFoundException e) { b = null; }  // booking removed in Bookings: show the truck, not a crash
            out.add(toReservation(r, byId.get(r.getOfferingId()), b));
        }
        return out;
    }

    @Transactional
    public ReservationResponse cancel(TenantId tenantId, UUID id, String reason) {
        FuelTruckStopReservation r = findReservation(tenantId, id);
        return outcome(tenantId, r, bookings.cancel(tenantId, r.getBookingId(), reason == null || reason.isBlank() ? "Cancelled at the truck stop" : reason.trim()));
    }

    @Transactional
    public ReservationResponse complete(TenantId tenantId, UUID id) {
        FuelTruckStopReservation r = findReservation(tenantId, id);
        return outcome(tenantId, r, bookings.complete(tenantId, r.getBookingId()));
    }

    @Transactional
    public ReservationResponse noShow(TenantId tenantId, UUID id) {
        FuelTruckStopReservation r = findReservation(tenantId, id);
        return outcome(tenantId, r, bookings.noShow(tenantId, r.getBookingId()));
    }

    // ---- helpers ----

    private ReservationResponse outcome(TenantId tenantId, FuelTruckStopReservation r, BookingInfo b) {
        return toReservation(r, offerings.findOwned(tenantId, r.getOfferingId()).orElse(null), b);
    }

    private static String notes(ReserveRequest req, FuelTruckStopReservation probe) {
        String n = "Truck stop: " + probe.getVehicleRegistration();
        return req.notes() == null || req.notes().isBlank() ? n : n + ". " + req.notes().trim();
    }

    private Map<UUID, ServiceInfo> servicesById(TenantId tenantId) {
        return bookings.services(tenantId).stream().collect(Collectors.toMap(ServiceInfo::id, Function.identity(), (a, b) -> a));
    }

    private OfferingResponse toResponse(TenantId tenantId, FuelTruckStopOffering o, ServiceInfo s) {
        List<Resource> resources = s == null ? List.of() : bookings.resources(tenantId, s.id()).stream().map(x -> new Resource(x.id(), x.name())).toList();
        return new OfferingResponse(o.getId(), o.getBookingsServiceId(), s == null ? "(service removed in Bookings)" : s.name(), o.getKind(), o.getDepotId(), o.isActive(),
                s == null ? 0 : s.durationMinutes(), s == null ? null : s.price(), resources, s == null);
    }

    private static ReservationResponse toReservation(FuelTruckStopReservation r, FuelTruckStopOffering o, BookingInfo b) {
        return new ReservationResponse(r.getId(), r.getOfferingId(), o == null ? null : o.getKind(), b == null ? null : b.serviceName(), r.getBookingId(),
                b == null ? null : b.bookingNumber(), r.getVehicleRegistration(), r.getDriverName(), r.getDriverPhone(), b == null ? null : b.resourceName(),
                b == null ? null : b.date(), b == null ? null : b.start(), b == null ? null : b.end(), b == null ? "UNKNOWN" : b.status(), b == null ? null : b.price());
    }

    private FuelTruckStopOffering findOffering(TenantId tenantId, UUID id) {
        return offerings.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Truck stop service", String.valueOf(id)));
    }

    private FuelTruckStopReservation findReservation(TenantId tenantId, UUID id) {
        return reservations.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Truck stop reservation", String.valueOf(id)));
    }
}

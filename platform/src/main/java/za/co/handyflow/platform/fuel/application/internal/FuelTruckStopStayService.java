package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopParking;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopParkingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.dto.StayDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phase 7d: overnight parking, owned by Fuel because Bookings only holds a slot within one day. The nightly rate and the number of bays
 * are the company's own settings; with no bay count nothing is capacity-limited. A stay keeps the rate it was booked at. Nothing is
 * invoiced here; the amount is what the stay comes to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelTruckStopStayService {

    private final FuelTruckStopStayRepository stays;
    private final FuelTruckStopParkingRepository parking;

    @Transactional(readOnly = true)
    public SettingsView settings(TenantId tenantId) {
        return parking.findForTenant(tenantId).map(p -> new SettingsView(p.getNightlyRate(), p.getBays())).orElse(new SettingsView(null, null));
    }

    @Transactional
    public SettingsView saveSettings(TenantId tenantId, SettingsRequest req, UUID by) {
        FuelTruckStopParking p = parking.findForTenant(tenantId).orElseGet(() -> FuelTruckStopParking.forTenant(tenantId));
        p.update(req.nightlyRate(), req.bays(), by);
        parking.save(p);
        return new SettingsView(p.getNightlyRate(), p.getBays());
    }

    @Transactional(readOnly = true)
    public List<StayView> list(TenantId tenantId, int limit, LocalDate today) {
        return stays.findRecent(tenantId, PageRequest.of(0, Math.max(1, Math.min(limit, 500)))).stream().map(s -> view(s, today)).toList();
    }

    @Transactional
    public StayView book(TenantId tenantId, BookRequest req, UUID by, LocalDate today) {
        parking.lockForTenant(tenantId); // serialise bookings so the last bay cannot be given twice
        var p = parking.findForTenant(tenantId).orElse(null);
        if (p == null || p.getNightlyRate() == null) throw new IllegalStateException("Set the nightly rate first, under the parking settings.");
        if (req.arrivalDate() != null && req.arrivalDate().isBefore(today))
            throw new IllegalArgumentException("The arrival cannot be in the past. For a truck already here, use today's date.");
        FuelTruckStopStay s = FuelTruckStopStay.book(tenantId, req.vehicleRegistration(), req.driverName(), req.driverPhone(), req.arrivalDate(), req.departureDate(),
                p.getNightlyRate(), req.note(), by);
        requireRoom(tenantId, p.getBays(), s.getArrivalDate(), s.getDepartureDate(), null, today);
        stays.save(s);
        log.info("Overnight stay booked for {} from {} to {}", s.getVehicleRegistration(), s.getArrivalDate(), s.getDepartureDate());
        return view(s, today);
    }

    @Transactional
    public StayView checkIn(TenantId tenantId, UUID id, LocalDate today) {
        FuelTruckStopStay s = find(tenantId, id);
        if (today.isBefore(s.getArrivalDate())) throw new IllegalStateException("This truck is not due until " + s.getArrivalDate() + ".");
        s.checkIn();
        return view(stays.save(s), today);
    }

    @Transactional
    public StayView checkOut(TenantId tenantId, UUID id, CheckOutRequest req, LocalDate today) {
        FuelTruckStopStay s = find(tenantId, id);
        s.checkOut(req == null || req.date() == null ? today : req.date());
        return view(stays.save(s), today);
    }

    /** Moves the planned departure. Extra nights need a free bay on each of them. */
    @Transactional
    public StayView changeDeparture(TenantId tenantId, UUID id, DepartureRequest req, LocalDate today) {
        parking.lockForTenant(tenantId);
        FuelTruckStopStay s = find(tenantId, id);
        var oldDeparture = s.getDepartureDate();
        s.changeDeparture(req.departureDate());
        if (s.getDepartureDate().isAfter(oldDeparture)) {
            var p = parking.findForTenant(tenantId).orElse(null);
            requireRoom(tenantId, p == null ? null : p.getBays(), oldDeparture, s.getDepartureDate(), s.getId(), today);
        }
        return view(stays.save(s), today);
    }

    @Transactional
    public StayView cancel(TenantId tenantId, UUID id, CancelRequest req, LocalDate today) {
        FuelTruckStopStay s = find(tenantId, id);
        s.cancel(req.reason());
        return view(stays.save(s), today);
    }

    @Transactional
    public StayView noShow(TenantId tenantId, UUID id, LocalDate today) {
        FuelTruckStopStay s = find(tenantId, id);
        s.noShow(today);
        return view(stays.save(s), today);
    }

    @Transactional(readOnly = true)
    public Occupancy occupancy(TenantId tenantId, LocalDate date) {
        Integer bays = parking.findForTenant(tenantId).map(FuelTruckStopParking::getBays).orElse(null);
        // From the day before, so a stay leaving on this date is still seen for the departures count.
        var held = stays.findHoldingBays(tenantId, date.minusDays(1), date.plusDays(1));
        int inYard = (int) held.stream().filter(s -> occupies(s, date, date)).count();
        int arrivals = (int) held.stream().filter(s -> FuelTruckStopStay.BOOKED.equals(s.getStatus()) && s.getArrivalDate().equals(date)).count();
        int departures = (int) held.stream().filter(s -> s.getDepartureDate().equals(date)).count();
        return new Occupancy(date, inYard, arrivals, departures, bays, bays == null ? null : Math.max(0, bays - inYard));
    }

    /**
     * Bays used and free for each of the next {@code days} nights (1 to 31), counting a night with the same rule that stops a
     * double booking, plus the trucks still in the yard past their planned departure. A read only view; nothing is changed.
     */
    @Transactional(readOnly = true)
    public CapacityView capacity(TenantId tenantId, LocalDate from, int days, LocalDate today) {
        int n = Math.max(1, Math.min(days, 31));
        Integer bays = parking.findForTenant(tenantId).map(FuelTruckStopParking::getBays).orElse(null);
        List<FuelTruckStopStay> held = stays.findHoldingBays(tenantId, from, from.plusDays(n));
        List<NightView> nights = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            final LocalDate night = from.plusDays(i);
            int used = (int) held.stream().filter(s -> occupies(s, night, today)).count();
            int arrivals = (int) held.stream().filter(s -> FuelTruckStopStay.BOOKED.equals(s.getStatus()) && s.getArrivalDate().equals(night)).count();
            int departures = (int) held.stream().filter(s -> s.getDepartureDate().equals(night)).count();
            nights.add(new NightView(night, used, arrivals, departures, bays == null ? null : Math.max(0, bays - used)));
        }
        List<OverstayView> over = held.stream().filter(s -> s.isOverstaying(today))
                .map(s -> new OverstayView(s.getId(), s.getVehicleRegistration(), s.getDepartureDate(),
                        java.time.temporal.ChronoUnit.DAYS.between(s.getDepartureDate(), today)))
                .sorted(java.util.Comparator.comparing(OverstayView::plannedDeparture)).toList();
        return new CapacityView(from, bays, nights, over);
    }

    private void requireRoom(TenantId tenantId, Integer bays, LocalDate from, LocalDate to, UUID excludeId, LocalDate today) {
        if (bays == null) return;
        var held = stays.findHoldingBays(tenantId, from, to).stream().filter(s -> !s.getId().equals(excludeId)).toList();
        for (LocalDate night = from; night.isBefore(to); night = night.plusDays(1)) {
            final LocalDate n = night;
            long used = held.stream().filter(s -> occupies(s, n, today)).count();
            if (used >= bays) throw new IllegalStateException("No bay is free on the night of " + n + ": all " + bays + " are taken.");
        }
    }

    /** Whether a stay holds a bay on a night. A truck still in the yard past its planned departure holds one up to today. */
    static boolean occupies(FuelTruckStopStay s, LocalDate night, LocalDate today) {
        if (!s.holdsBay() || night.isBefore(s.getArrivalDate())) return false;
        return night.isBefore(s.getDepartureDate()) || (FuelTruckStopStay.CHECKED_IN.equals(s.getStatus()) && !night.isAfter(today));
    }

    private FuelTruckStopStay find(TenantId tenantId, UUID id) {
        return stays.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Stay", id.toString()));
    }

    static StayView view(FuelTruckStopStay s, LocalDate today) {
        return new StayView(s.getId(), s.getVehicleRegistration(), s.getDriverName(), s.getDriverPhone(), s.getArrivalDate(), s.getDepartureDate(), s.getActualDepartureDate(),
                s.getStatus(), s.getRatePerNight(), s.nights(), s.amount(), s.getNote(), s.getCancelledReason(), s.isOverstaying(today), s.getCheckedInAt(), s.getCheckedOutAt());
    }
}

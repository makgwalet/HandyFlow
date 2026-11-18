package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelPump;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.repository.FuelPumpRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.fuel.dto.StayDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The door for writes a phone may have queued while offline: shift open and close, and stay book, check-in and check-out.
 * The existing services still do the work and still decide; this adds three things around them.
 *   1. Replay: a request id already used for the same thing returns the first answer instead of doing it twice.
 *   2. Sequence and age: a captured time that is in the future or too old, and an opening meter below the pump's last meter, are refused.
 *   3. Conflict log: a refusal is kept (own transaction) for a supervisor; nothing half-done is left behind.
 * A request with no id is an ordinary online write and goes straight through, so the web screens and older apps are unchanged.
 * Permissions are checked by the controllers on every call, including a late one, so a revoked permission stops a queued write.
 * Payment settlement stays with the financial integration; a stay's invoice is raised by the stay's own invoicing, never here.
 */
@Service
@RequiredArgsConstructor
public class FuelOfflineService {

    private final FuelForecourtService forecourt;
    private final FuelTruckStopStayService stayService;
    private final FuelShiftRepository shifts;
    private final FuelTruckStopStayRepository stays;
    private final FuelPumpRepository pumps;
    private final FuelOfflineConflictService conflicts;
    private final FuelSettingsService settings;
    private final AuditTrailFacade audit;

    // ── Forecourt ─────────────────────────────────────────────────────────────

    @Transactional
    public ShiftResponse openShift(TenantId tenant, OpenShiftRequest req, UUID userId, Instant now) {
        UUID rid = req.clientRequestId();
        if (rid == null) return forecourt.openShift(tenant, req, userId);
        Optional<FuelShift> done = shifts.findByOpenRequest(tenant, rid);
        if (done.isPresent()) return forecourt.getShift(tenant, done.get().getId());
        return guard(tenant, FuelOfflineConflictService.SHIFT_OPEN, rid, null, userId, req.capturedAt(), () -> {
            checkCaptured(tenant, req.capturedAt(), now);
            requireMetersNotBackwards(tenant, req.readings());
            requirePricesUnchanged(tenant, req.readings());
            ShiftResponse out = forecourt.openShift(tenant, req, userId);
            FuelShift s = shifts.findByTenantAndId(tenant, out.id()).orElseThrow(() -> new ResourceNotFoundException("Shift", out.id().toString()));
            s.stampOpen(rid, req.capturedAt());
            shifts.saveAndFlush(s);
            audit.record(tenant, "FUEL_SHIFT_OPENED_OFFLINE_SYNC", "FuelShift", s.getId(), Map.of(), Map.of("clientRequestId", rid.toString(), "capturedAt", String.valueOf(req.capturedAt())));
            return forecourt.getShift(tenant, s.getId());
        });
    }

    /** Closes a shift the phone knows by the id it made when it opened it, because a shift opened offline has no server id yet. */
    @Transactional
    public ShiftResponse closeByOpenRequest(TenantId tenant, UUID openRequestId, CloseShiftRequest req, UUID userId, Instant now) {
        FuelShift s = shifts.findByOpenRequest(tenant, openRequestId).orElse(null);
        if (s == null) {
            String why = "The shift this close belongs to has not reached the server, or was refused. Nothing was closed.";
            if (req.clientRequestId() != null) conflicts.record(tenant, FuelOfflineConflictService.SHIFT_CLOSE, req.clientRequestId(), null, userId, why, req.capturedAt());
            throw new ResourceNotFoundException("Shift", openRequestId.toString());
        }
        return closeShift(tenant, s.getId(), req, userId, now);
    }

    @Transactional
    public ShiftResponse closeShift(TenantId tenant, UUID shiftId, CloseShiftRequest req, UUID userId, Instant now) {
        UUID rid = req.clientRequestId();
        if (rid == null) return forecourt.closeShift(tenant, shiftId, req, userId);
        Optional<FuelShift> done = shifts.findByCloseRequest(tenant, rid);
        if (done.isPresent()) {
            // A resend of a close that worked. Only for the same shift: the same id on another shift is a bug on the phone, not a replay.
            if (!done.get().getId().equals(shiftId)) throw new IllegalStateException("That request id was already used for a different shift.");
            return forecourt.getShift(tenant, shiftId);
        }
        return guard(tenant, FuelOfflineConflictService.SHIFT_CLOSE, rid, shiftId, userId, req.capturedAt(), () -> {
            checkCaptured(tenant, req.capturedAt(), now);
            ShiftResponse out = forecourt.closeShift(tenant, shiftId, req, userId);
            FuelShift s = shifts.findByTenantAndId(tenant, shiftId).orElseThrow(() -> new ResourceNotFoundException("Shift", shiftId.toString()));
            s.stampClose(rid, req.capturedAt());
            shifts.saveAndFlush(s);
            audit.record(tenant, "FUEL_SHIFT_CLOSED_OFFLINE_SYNC", "FuelShift", shiftId, Map.of(), Map.of("clientRequestId", rid.toString(), "capturedAt", String.valueOf(req.capturedAt())));
            return forecourt.getShift(tenant, shiftId);
        });
    }

    /** The first reading of a shift cannot be below what the pump last showed: a meter does not run backwards. Online keeps its existing gap handling. */
    private void requireMetersNotBackwards(TenantId tenant, List<OpenReading> readings) {
        Set<UUID> ids = readings.stream().map(OpenReading::pumpId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, FuelPump> map = pumps.findActiveByIds(tenant, ids).stream().collect(Collectors.toMap(FuelPump::getId, p -> p));
        for (OpenReading r : readings) {
            FuelPump p = map.get(r.pumpId());
            if (p != null && p.getLastMeter() != null && r.openingMeter().compareTo(p.getLastMeter()) < 0)
                throw new IllegalStateException("The opening meter for " + p.getName() + " (" + r.openingMeter().toPlainString() + ") is below the last meter on record ("
                        + p.getLastMeter().toPlainString() + "). A meter does not run backwards. Check the reading, or ask a supervisor.");
        }
    }

    /**
     * A shift sells at the pump price copied when it opens. For a shift opened offline that is the price on the server when the open syncs,
     * which may not be the price the attendant sold at. The phone sends the price it showed; if the pump has a different price now the open is
     * refused (and kept in the conflict log) so a supervisor settles it, rather than the takings being quietly judged at another price.
     */
    private void requirePricesUnchanged(TenantId tenant, List<OpenReading> readings) {
        List<OpenReading> priced = readings.stream().filter(r -> r.expectedPrice() != null).toList();
        if (priced.isEmpty()) return;
        Set<UUID> ids = priced.stream().map(OpenReading::pumpId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, FuelPump> map = pumps.findActiveByIds(tenant, ids).stream().collect(Collectors.toMap(FuelPump::getId, p -> p));
        for (OpenReading r : priced) {
            FuelPump p = map.get(r.pumpId());
            if (p != null && p.getPricePerLitre() != null && r.expectedPrice().compareTo(p.getPricePerLitre()) != 0)
                throw new IllegalStateException("The price of " + p.getName() + " is R " + p.getPricePerLitre().toPlainString() + " per litre now, but the phone opened the shift at R "
                        + r.expectedPrice().toPlainString() + ". The takings would not agree. Ask a supervisor to settle it before the shift is opened.");
        }
    }

    // ── Truck stop ────────────────────────────────────────────────────────────

    @Transactional
    public StayView book(TenantId tenant, BookRequest req, UUID userId, LocalDate today, Instant now) {
        UUID rid = req.clientRequestId();
        if (rid == null) return stayService.book(tenant, req, userId, today);
        Optional<FuelTruckStopStay> done = stays.findByBookRequest(tenant, rid);
        if (done.isPresent()) return stayService.get(tenant, done.get().getId(), today);
        return guard(tenant, FuelOfflineConflictService.STAY_BOOK, rid, null, userId, req.capturedAt(), () -> {
            checkCaptured(tenant, req.capturedAt(), now);
            // The phone never claimed a bay was free; the server decides now, on the bays it has now. A full yard refuses it here.
            StayView out = stayService.book(tenant, req, userId, today);
            FuelTruckStopStay s = stays.findOwned(tenant, out.id()).orElseThrow(() -> new ResourceNotFoundException("Stay", out.id().toString()));
            s.stampBook(rid);
            stays.saveAndFlush(s);
            audit.record(tenant, "FUEL_STAY_BOOKED_OFFLINE_SYNC", "FuelTruckStopStay", s.getId(), Map.of(), Map.of("clientRequestId", rid.toString(), "capturedAt", String.valueOf(req.capturedAt())));
            return stayService.get(tenant, s.getId(), today);
        });
    }

    @Transactional
    public StayView checkIn(TenantId tenant, UUID stayId, CheckInRequest req, UUID userId, LocalDate today, Instant now) {
        UUID rid = req == null ? null : req.clientRequestId();
        if (rid == null) return stayService.checkIn(tenant, stayId, today);
        Optional<FuelTruckStopStay> done = stays.findByCheckInRequest(tenant, rid);
        if (done.isPresent()) {
            if (!done.get().getId().equals(stayId)) throw new IllegalStateException("That request id was already used for a different stay.");
            return stayService.get(tenant, stayId, today);
        }
        return guard(tenant, FuelOfflineConflictService.STAY_CHECK_IN, rid, stayId, userId, req.capturedAt(), () -> {
            checkCaptured(tenant, req.capturedAt(), now);
            StayView out = stayService.checkIn(tenant, stayId, today);
            FuelTruckStopStay s = stays.findOwned(tenant, stayId).orElseThrow(() -> new ResourceNotFoundException("Stay", stayId.toString()));
            s.stampCheckIn(rid, req.capturedAt());
            stays.saveAndFlush(s);
            audit.record(tenant, "FUEL_STAY_CHECKED_IN_OFFLINE_SYNC", "FuelTruckStopStay", stayId, Map.of(), Map.of("clientRequestId", rid.toString(), "capturedAt", String.valueOf(req.capturedAt())));
            return out;
        });
    }

    @Transactional
    public StayView checkOut(TenantId tenant, UUID stayId, CheckOutRequest req, UUID userId, LocalDate today, Instant now) {
        UUID rid = req == null ? null : req.clientRequestId();
        if (rid == null) return stayService.checkOut(tenant, stayId, req, today);
        Optional<FuelTruckStopStay> done = stays.findByCheckOutRequest(tenant, rid);
        if (done.isPresent()) {
            if (!done.get().getId().equals(stayId)) throw new IllegalStateException("That request id was already used for a different stay.");
            return stayService.get(tenant, stayId, today);
        }
        return guard(tenant, FuelOfflineConflictService.STAY_CHECK_OUT, rid, stayId, userId, req.capturedAt(), () -> {
            checkCaptured(tenant, req.capturedAt(), now);
            // Pricing is the server's: nights and amount come from the stay's own rate and the departure date, never from the phone.
            // The phone fixes the date the truck left when it was captured; one in the future is wrong, and a late sync must not add nights.
            if (req.date() != null && req.date().isAfter(today)) throw new IllegalArgumentException("The departure date is in the future.");
            StayView out = stayService.checkOut(tenant, stayId, req, today);
            FuelTruckStopStay s = stays.findOwned(tenant, stayId).orElseThrow(() -> new ResourceNotFoundException("Stay", stayId.toString()));
            s.stampCheckOut(rid, req.capturedAt());
            stays.saveAndFlush(s);
            audit.record(tenant, "FUEL_STAY_CHECKED_OUT_OFFLINE_SYNC", "FuelTruckStopStay", stayId, Map.of(), Map.of("clientRequestId", rid.toString(), "capturedAt", String.valueOf(req.capturedAt())));
            return stayService.get(tenant, stayId, today);
        });
    }

    // ── shared ────

    /** The company's own limits from Fuel settings; the defaults when none are set. */
    private void checkCaptured(TenantId tenant, Instant capturedAt, Instant now) {
        if (capturedAt == null) return;
        var p = settings.offlinePolicy(tenant);
        FuelOfflinePolicy.checkCaptured(capturedAt, now, java.time.Duration.ofHours(p.maxAgeHours()), java.time.Duration.ofMinutes(p.clockSkewMinutes()));
    }

    /** Runs the write; if the server refuses it, keeps a conflict row (own transaction) and passes the refusal on unchanged. */
    private <T> T guard(TenantId tenant, String kind, UUID rid, UUID entityId, UUID userId, Instant capturedAt, Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException | IllegalArgumentException | ResourceNotFoundException e) {
            conflicts.record(tenant, kind, rid, entityId, userId, e.getMessage(), capturedAt);
            throw e;
        }
    }
}

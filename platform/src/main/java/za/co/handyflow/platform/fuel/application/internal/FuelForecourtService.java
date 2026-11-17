package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Retail forecourt. A shift opens with a meter reading per pump and closes with the closing readings and the takings.
 * Litres sold = meter movement minus test litres. Stock leaves the tank (one ledger line per tank) when the shift closes.
 * The takings variance is recorded and explained, never written off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelForecourtService {

    private final FuelPumpRepository pumps;
    private final FuelShiftRepository shifts;
    private final FuelShiftReadingRepository readings;
    private final FuelTankRepository tanks;
    private final FuelStockLedger ledger;
    private final ShiftNumberGenerator numbers;
    private final FuelCashControlService cash;
    private final FuelToleranceService tolerances;
    private final FuelShiftAlertService shiftAlerts;

    // ── Pumps ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PumpResponse> listPumps(TenantId tenantId) {
        List<FuelPump> all = pumps.findAllActive(tenantId);
        Map<UUID, FuelTank> tankMap = tankMap(tenantId);
        Set<UUID> busy = all.isEmpty() ? Set.of() : readings.findOpenForPumps(tenantId, all.stream().map(FuelPump::getId).toList())
                .stream().map(FuelShiftReading::getPumpId).collect(Collectors.toSet());
        return all.stream().map(p -> toPump(p, tankMap.get(p.getTankId()), busy.contains(p.getId()))).toList();
    }

    @Transactional
    public PumpResponse createPump(TenantId tenantId, PumpRequest req) {
        FuelTank tank = tank(tenantId, req.tankId());
        FuelPump created = FuelPump.create(tenantId, req.name(), tank.getId(), req.pricePerLitre());
        created.assignNozzle(req.dispenserLabel(), req.nozzleNumber());
        requireNozzleFree(tenantId, created, null);
        return toPump(pumps.save(created), tank, false);
    }

    @Transactional
    public PumpResponse updatePump(TenantId tenantId, UUID id, PumpRequest req) {
        FuelPump p = pump(tenantId, id);
        boolean busy = isOnOpenShift(tenantId, id);
        boolean priceOrTankChanged = p.getPricePerLitre().compareTo(req.pricePerLitre()) != 0 || !p.getTankId().equals(req.tankId());
        if (busy && priceOrTankChanged) {
            throw new IllegalStateException("This pump is on an open shift. Close the shift before changing its price or tank.");
        }
        FuelTank tank = tank(tenantId, req.tankId());
        boolean active = req.active() == null ? p.isActive() : req.active();
        if (busy && !active) throw new IllegalStateException("This pump is on an open shift. Close the shift before deactivating it.");
        p.update(req.name(), tank.getId(), req.pricePerLitre(), active);
        // Null in both leaves the grouping alone, so an older client that does not send them cannot ungroup a pump.
        if (req.dispenserLabel() != null || req.nozzleNumber() != null) {
            p.assignNozzle(req.dispenserLabel(), req.nozzleNumber());
            requireNozzleFree(tenantId, p, p.getId());
        }
        return toPump(pumps.save(p), tank, busy);
    }

    @Transactional
    public void deletePump(TenantId tenantId, UUID id) {
        FuelPump p = pump(tenantId, id);
        if (isOnOpenShift(tenantId, id)) throw new IllegalStateException("This pump is on an open shift. Close the shift first.");
        p.softDelete();
        pumps.save(p);
    }

    // ── Shifts ───────────────────────────────────────────────────────────────

    @Transactional
    public ShiftResponse openShift(TenantId tenantId, OpenShiftRequest req, UUID userId) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (OpenReading r : req.readings()) {
            if (!ids.add(r.pumpId())) throw new IllegalArgumentException("A pump appears twice in the opening readings.");
        }
        Map<UUID, FuelPump> pumpMap = pumps.findActiveByIds(tenantId, ids).stream().collect(Collectors.toMap(FuelPump::getId, Function.identity()));
        for (UUID id : ids) if (!pumpMap.containsKey(id)) throw new ResourceNotFoundException("Pump", id.toString());
        for (FuelPump p : pumpMap.values()) {
            if (!p.isActive()) throw new IllegalStateException(p.getName() + " is not active.");
        }
        Set<UUID> busy = readings.findOpenForPumps(tenantId, ids).stream().map(FuelShiftReading::getPumpId).collect(Collectors.toSet());
        if (!busy.isEmpty()) {
            String names = busy.stream().map(i -> pumpMap.get(i).getName()).sorted().collect(Collectors.joining(", "));
            throw new IllegalStateException("Already on an open shift: " + names + ". Close that shift first.");
        }
        Map<UUID, FuelTank> tankMap = tankMap(tenantId);
        FuelShift shift = shifts.save(FuelShift.open(tenantId, numbers.generate(tenantId), req.attendantName(), userId, req.notes()));
        for (OpenReading r : req.readings()) {
            FuelPump p = pumpMap.get(r.pumpId());
            FuelTank t = tankMap.get(p.getTankId());
            if (t == null) throw new IllegalStateException(p.getName() + " is linked to a tank that no longer exists.");
            BigDecimal gap = p.getLastMeter() == null ? BigDecimal.ZERO : r.openingMeter().subtract(p.getLastMeter());
            readings.save(FuelShiftReading.open(tenantId, shift.getId(), p, t.getFuelType(), r.openingMeter(), gap));
        }
        log.info("Forecourt shift opened {} pumps={} by={}", shift.getShiftNumber(), ids.size(), userId);
        return toShift(shift, readings.findByShift(tenantId, shift.getId()), pumpNames(tenantId));
    }

    @Transactional
    public ShiftResponse closeShift(TenantId tenantId, UUID shiftId, CloseShiftRequest req, UUID userId) {
        FuelShift shift = shift(tenantId, shiftId);
        if (!shift.isOpen()) throw new IllegalStateException("This shift is already " + shift.getStatus().toLowerCase() + ".");
        List<FuelShiftReading> rows = readings.findByShift(tenantId, shiftId);
        Map<UUID, CloseReading> given = new HashMap<>();
        for (CloseReading c : req.readings()) {
            if (given.put(c.pumpId(), c) != null) throw new IllegalArgumentException("A pump appears twice in the closing readings.");
        }
        Map<UUID, FuelPump> pumpMap = pumps.findAllActive(tenantId).stream().collect(Collectors.toMap(FuelPump::getId, Function.identity()));
        for (UUID id : given.keySet()) {
            if (rows.stream().noneMatch(r -> r.getPumpId().equals(id))) throw new IllegalArgumentException("A closing reading was given for a pump that is not on this shift.");
        }
        Map<UUID, FuelTank> tankMap = tankMap(tenantId);
        BigDecimal expected = BigDecimal.ZERO, litres = BigDecimal.ZERO;
        Map<UUID, BigDecimal> perTank = new TreeMap<>();
        for (FuelShiftReading r : rows) {
            CloseReading c = given.get(r.getPumpId());
            String pumpName = pumpMap.containsKey(r.getPumpId()) ? pumpMap.get(r.getPumpId()).getName() : "a pump";
            if (c == null) throw new IllegalArgumentException("Enter the closing meter for " + pumpName + ".");
            FuelTank t = tankMap.get(r.getTankId());
            r.close(c.closingMeter(), c.testLitres(), t == null ? null : t.getCostPerLitreWac(), pumpName);
            expected = expected.add(r.getSalesValue());
            litres = litres.add(r.getLitresSold());
            if (r.getLitresSold().signum() > 0) perTank.merge(r.getTankId(), r.getLitresSold(), BigDecimal::add);
        }
        // Validate the takings before touching stock, so a refusal leaves nothing half done.
        long pending = cash.pendingCount(tenantId, shiftId);
        if (pending > 0) throw new IllegalStateException(pending + " refund, void or discount entr" + (pending == 1 ? "y is" : "ies are")
                + " still waiting for a supervisor. Decide or withdraw " + (pending == 1 ? "it" : "them") + " before closing the shift.");
        FuelCashControlService.CashPolicy policy = cash.policy(tenantId);
        // A depot may have its own tolerance; a shift that drew on several uses the smallest.
        Set<UUID> depotIds = new HashSet<>();
        for (FuelShiftReading r : rows) { FuelTank t = tankMap.get(r.getTankId()); if (t != null && t.getDepotId() != null) depotIds.add(t.getDepotId()); }
        BigDecimal tolerance = tolerances.cashToleranceFor(tenantId, depotIds, policy.tolerance());
        shift.close(req.cash(), req.card(), req.fleetCard(), req.account(), req.other(), expected, cash.approvedTotal(tenantId, shiftId), litres,
                req.varianceReason(), userId, tolerance, policy.requireSeparateCloser());

        // Lock tanks in id order (TreeMap) so two shifts closing together cannot deadlock.
        for (Map.Entry<UUID, BigDecimal> e : perTank.entrySet()) {
            FuelTank tank = tanks.findActiveByIdForUpdate(tenantId, e.getKey()).orElseThrow(() -> new ResourceNotFoundException("Tank", e.getKey().toString()));
            ledger.requireUnreserved(tank, e.getValue(), BigDecimal.ZERO);
            try {
                tank.removeStock(e.getValue());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("The meters say " + e.getValue().toPlainString() + "L was sold from " + tank.getName() + " but the book stock is only "
                        + tank.getCurrentLitres().toPlainString() + "L. Record the latest delivery or a dip adjustment first, then close the shift.");
            }
            tanks.save(tank);
            ledger.post(tank, FuelStockMovement.Type.DISPATCH, e.getValue().negate(), "FORECOURT_SHIFT", shiftId, "Forecourt sales " + shift.getShiftNumber());
        }
        for (FuelShiftReading r : rows) {
            FuelPump p = pumpMap.get(r.getPumpId());
            if (p != null) { p.recordMeter(r.getClosingMeter()); pumps.save(p); }
        }
        readings.saveAll(rows);
        shifts.save(shift);
        log.info("Forecourt shift closed {} litres={} variance={} by={}", shift.getShiftNumber(), litres, shift.getVarianceAmount(), userId);
        shiftAlerts.afterClose(tenantId, shift);
        return toShift(shift, rows, pumpNames(tenantId));
    }

    @Transactional
    public ShiftResponse reverseShift(TenantId tenantId, UUID shiftId, ReverseShiftRequest req, UUID userId) {
        FuelShift shift = shift(tenantId, shiftId);
        if (!shift.isClosed()) throw new IllegalStateException("Only a closed shift can be reversed.");
        List<FuelShiftReading> rows = readings.findByShift(tenantId, shiftId);
        Map<UUID, BigDecimal> perTank = new TreeMap<>();
        for (FuelShiftReading r : rows) if (r.getLitresSold() != null && r.getLitresSold().signum() > 0) perTank.merge(r.getTankId(), r.getLitresSold(), BigDecimal::add);
        shift.reverse(req.reason(), userId);
        for (Map.Entry<UUID, BigDecimal> e : perTank.entrySet()) {
            FuelTank tank = tanks.findActiveByIdForUpdate(tenantId, e.getKey()).orElseThrow(() -> new ResourceNotFoundException("Tank", e.getKey().toString()));
            tank.restoreStock(e.getValue());
            tanks.save(tank);
            ledger.post(tank, FuelStockMovement.Type.REVERSAL, e.getValue(), "FORECOURT_SHIFT", shiftId, req.reason().trim());
        }
        shifts.save(shift);
        log.info("Forecourt shift reversed {} by={}", shift.getShiftNumber(), userId);
        return toShift(shift, rows, pumpNames(tenantId));
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> listShifts(TenantId tenantId, int limit) { return listShifts(tenantId, limit, null); }

    /**
     * {@code allowedTankIds} null means no depot limit. Otherwise a shift is listed only when every pump read in it is on one of those tanks, because
     * a shift's takings cover all of its pumps. A limited user is given a full page of their own shifts where the history has enough (up to 20 pages back).
     */
    @Transactional(readOnly = true)
    public List<ShiftResponse> listShifts(TenantId tenantId, int limit, Set<UUID> allowedTankIds) {
        int want = Math.max(1, Math.min(limit, 200));
        Map<UUID, String> names = pumpNames(tenantId);
        List<ShiftResponse> out = new java.util.ArrayList<>();
        // A depot-limited user is given a full page of their own shifts: keep reading further back until there are enough or the history ends (bounded).
        int maxPages = allowedTankIds == null ? 1 : 20;
        for (int page = 0; page < maxPages && out.size() < want; page++) {
            List<FuelShift> list = shifts.findRecent(tenantId, PageRequest.of(page, want));
            if (list.isEmpty()) break;
            Map<UUID, List<FuelShiftReading>> byShift = readings.findByShifts(tenantId, list.stream().map(FuelShift::getId).toList())
                    .stream().collect(Collectors.groupingBy(FuelShiftReading::getShiftId));
            for (FuelShift s : list) {
                List<FuelShiftReading> rs = byShift.getOrDefault(s.getId(), List.of());
                if (visible(allowedTankIds, rs) && out.size() < want) out.add(toShift(s, rs, names));
            }
            if (list.size() < want) break;
        }
        return out;
    }

    private static boolean visible(Set<UUID> allowedTankIds, List<FuelShiftReading> rs) {
        return FuelScopeService.shiftVisible(allowedTankIds, rs.stream().map(FuelShiftReading::getTankId).toList());
    }

    @Transactional(readOnly = true)
    public ShiftResponse getShift(TenantId tenantId, UUID id) { return getShift(tenantId, id, null); }

    /** A depot-limited user is refused a shift that touches a tank outside their depots. */
    @Transactional(readOnly = true)
    public ShiftResponse getShift(TenantId tenantId, UUID id, Set<UUID> allowedTankIds) {
        FuelShift s = shift(tenantId, id);
        List<FuelShiftReading> rs = readings.findByShift(tenantId, id);
        if (!visible(allowedTankIds, rs)) {
            throw new za.co.handyflow.platform.shared.HandyFlowException("Your access is limited to other depots, so you cannot see this shift. An administrator can change this under Fuel > Setup > Access limits.",
                    org.springframework.http.HttpStatus.FORBIDDEN, "FUEL_SCOPE_DEPOT");
        }
        return toShift(s, rs, pumpNames(tenantId));
    }

    @Transactional(readOnly = true)
    public ForecourtSummary summary(TenantId tenantId, int days) { return summary(tenantId, days, null); }

    /** {@code allowedTankIds} null means no depot limit; otherwise only shifts that sit wholly on those tanks are counted. */
    @Transactional(readOnly = true)
    public ForecourtSummary summary(TenantId tenantId, int days, Set<UUID> allowedTankIds) {
        int d = Math.max(1, Math.min(days, 366));
        List<FuelShift> closed = shifts.findClosedSince(tenantId, Instant.now().minus(d, ChronoUnit.DAYS));
        if (allowedTankIds != null && !closed.isEmpty()) {
            Map<UUID, List<FuelShiftReading>> by = readings.findByShifts(tenantId, closed.stream().map(FuelShift::getId).toList()).stream().collect(Collectors.groupingBy(FuelShiftReading::getShiftId));
            closed = closed.stream().filter(s -> visible(allowedTankIds, by.getOrDefault(s.getId(), List.of()))).toList();
        }
        if (closed.isEmpty()) return new ForecourtSummary(d, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of());
        BigDecimal litres = BigDecimal.ZERO, sales = BigDecimal.ZERO, variance = BigDecimal.ZERO;
        int flagged = 0;
        for (FuelShift s : closed) {
            litres = litres.add(s.getLitresSold() == null ? BigDecimal.ZERO : s.getLitresSold());
            sales = sales.add(s.getExpectedAmount() == null ? BigDecimal.ZERO : s.getExpectedAmount());
            BigDecimal v = s.getVarianceAmount() == null ? BigDecimal.ZERO : s.getVarianceAmount();
            variance = variance.add(v);
            if (s.isOutsideTolerance()) flagged++;
        }
        Map<String, BigDecimal[]> byFuel = new TreeMap<>();
        for (FuelShiftReading r : readings.findByShifts(tenantId, closed.stream().map(FuelShift::getId).toList())) {
            if (r.getLitresSold() == null) continue;
            BigDecimal[] a = byFuel.computeIfAbsent(r.getFuelType(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(r.getLitresSold());
            a[1] = a[1].add(r.getSalesValue());
        }
        List<FuelTotal> totals = byFuel.entrySet().stream().map(e -> new FuelTotal(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        return new ForecourtSummary(d, closed.size(), litres, sales, variance, flagged, totals);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean isOnOpenShift(TenantId tenantId, UUID pumpId) { return !readings.findOpenForPumps(tenantId, List.of(pumpId)).isEmpty(); }

    private FuelPump pump(TenantId t, UUID id) { return pumps.findActiveById(t, id).orElseThrow(() -> new ResourceNotFoundException("Pump", id.toString())); }
    /** Notes the times an import's provider reported. Does not change the system's own opened and closed times. */
    @Transactional
    public void recordProviderTimes(TenantId tenantId, UUID shiftId, Instant opened, Instant closed) {
        shift(tenantId, shiftId).recordProviderTimes(opened, closed);
    }

    private FuelShift shift(TenantId t, UUID id) { return shifts.findByTenantAndId(t, id).orElseThrow(() -> new ResourceNotFoundException("Shift", id.toString())); }
    private FuelTank tank(TenantId t, UUID id) { return tanks.findActiveById(t, id).orElseThrow(() -> new ResourceNotFoundException("Tank", id.toString())); }

    private Map<UUID, FuelTank> tankMap(TenantId t) { return tanks.findAllActive(t).stream().collect(Collectors.toMap(FuelTank::getId, Function.identity())); }
    private Map<UUID, String> pumpNames(TenantId t) { return pumps.findAllActive(t).stream().collect(Collectors.toMap(FuelPump::getId, FuelPump::getName)); }

    /** The database also enforces this; checking here gives a readable message instead of a constraint error. */
    private void requireNozzleFree(TenantId tenantId, FuelPump p, UUID selfId) {
        if (p.getDispenserLabel() == null || p.getNozzleNumber() == null) return;
        boolean taken = pumps.findAllActive(tenantId).stream().anyMatch(o -> !o.getId().equals(selfId) && o.getDispenserLabel() != null && o.getNozzleNumber() != null
                && o.getDispenserLabel().equalsIgnoreCase(p.getDispenserLabel()) && o.getNozzleNumber().equals(p.getNozzleNumber()));
        if (taken) throw new IllegalStateException("Nozzle " + p.getNozzleNumber() + " on " + p.getDispenserLabel() + " is already in use. Choose another number.");
    }

    private static PumpResponse toPump(FuelPump p, FuelTank t, boolean onShift) {
        return new PumpResponse(p.getId(), p.getName(), p.getTankId(), t == null ? null : t.getName(), t == null ? null : t.getFuelType(),
                p.getPricePerLitre(), p.getLastMeter(), p.isActive(), onShift, p.getDispenserLabel(), p.getNozzleNumber());
    }

    private static ShiftResponse toShift(FuelShift s, List<FuelShiftReading> rows, Map<UUID, String> names) {
        List<ReadingResponse> rr = rows.stream().map(r -> new ReadingResponse(r.getPumpId(), names.getOrDefault(r.getPumpId(), "Pump"), r.getFuelType(),
                r.getOpeningMeter(), r.getOpeningGap(), r.getClosingMeter(), r.getTestLitres(), r.getLitresSold(), r.getPricePerLitre(), r.getSalesValue())).toList();
        return new ShiftResponse(s.getId(), s.getShiftNumber(), s.getAttendantName(), s.getStatus(), s.getOpenedAt(), s.getClosedAt(),
                s.getCashAmount(), s.getCardAmount(), s.getAccountAmount(), s.getOtherAmount(), s.getExpectedAmount(), s.getLitresSold(),
                s.getVarianceAmount(), s.getVarianceReason(), s.getNotes(), s.getReverseReason(), rr, s.getFleetCardAmount(), s.getAdjustmentsAmount(),
                s.getToleranceApplied(), s.getReviewStatus(), s.getReviewNote(), s.getReviewedAt(), s.getProviderOpenedAt(), s.getProviderClosedAt());
    }
}

package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Matches the receipts in a forecourt's tanks to the dispatches that supplied it. A dispatch names the forecourt (a depot) it is delivered to; a person
 * links each receipt to its dispatch. The report shows, per forecourt, linked dispatches with litres sent against litres received, dispatches nothing
 * has been linked to yet, and receipts not linked to a dispatch. No tolerance is applied and nothing is matched automatically: the difference is shown
 * as it is and the company decides what is acceptable. A link needs the same fuel type, a receipt in a tank of the dispatch's destination, neither
 * side reversed, and the receipt not already linked.
 */
@Service
@RequiredArgsConstructor
public class FuelDispatchMatchService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelDispatchRepository dispatches;
    private final FuelReceiptRepository receipts;
    private final FuelDispatchReceiptMatchRepository matches;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;

    @Transactional(readOnly = true)
    public MatchingReport report(TenantId tenantId, int days, Set<UUID> allowedDepotIds) {
        int span = Math.max(1, Math.min(days, 366));
        LocalDate to = LocalDate.now(SAST), from = to.minusDays(span - 1L);
        var fromI = from.atStartOfDay(SAST).toInstant();
        var toI = to.plusDays(1).atStartOfDay(SAST).toInstant();

        Map<UUID, FuelDepot> depotById = new HashMap<>();
        for (FuelDepot d : depots.findAllActive(tenantId)) depotById.put(d.getId(), d);
        Map<UUID, FuelTank> tankById = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) tankById.put(t.getId(), t);

        // Forecourts in view: depots that are the destination of a dispatch in the period, or that hold a pump tank and received fuel.
        Set<UUID> visible = new LinkedHashSet<>();
        for (UUID id : depotById.keySet()) if (allowedDepotIds == null || allowedDepotIds.contains(id)) visible.add(id);
        if (visible.isEmpty()) return new MatchingReport(from, to, List.of());

        List<FuelDispatch> ds = dispatches.findToDepotsBetween(tenantId, visible, fromI, toI);
        Map<UUID, List<FuelReceipt>> receiptsByDepot = new HashMap<>();
        List<FuelReceipt> inPeriod = receipts.findReceivedBetween(tenantId, fromI, toI);
        Set<UUID> dispatchesWithDest = new HashSet<>();
        for (FuelDispatch d : ds) dispatchesWithDest.add(d.getDestinationDepotId());
        for (FuelReceipt r : inPeriod) {
            FuelTank t = tankById.get(r.getTankId());
            UUID depot = t == null ? null : t.getDepotId();
            // Only forecourts that actually receive supply runs are listed, so a depot that only buys from suppliers does not clutter the report.
            if (depot != null && visible.contains(depot) && dispatchesWithDest.contains(depot)) receiptsByDepot.computeIfAbsent(depot, k -> new ArrayList<>()).add(r);
        }
        Set<UUID> dispatchIds = new HashSet<>(), receiptIds = new HashSet<>();
        for (FuelDispatch d : ds) dispatchIds.add(d.getId());
        for (List<FuelReceipt> l : receiptsByDepot.values()) for (FuelReceipt r : l) receiptIds.add(r.getId());
        Map<UUID, FuelReceipt> receiptById = new HashMap<>();
        for (List<FuelReceipt> l : receiptsByDepot.values()) for (FuelReceipt r : l) receiptById.put(r.getId(), r);
        // Receipts linked to a dispatch in view may fall outside the period; fetch them so the litres received are complete.
        List<FuelDispatchReceiptMatch> ms = dispatchIds.isEmpty() && receiptIds.isEmpty() ? List.of() : matches.findForAny(tenantId, dispatchIds.isEmpty() ? Set.of(new UUID(0, 0)) : dispatchIds, receiptIds.isEmpty() ? Set.of(new UUID(0, 0)) : receiptIds);
        Set<UUID> missing = new HashSet<>();
        for (FuelDispatchReceiptMatch m : ms) if (!receiptById.containsKey(m.getReceiptId())) missing.add(m.getReceiptId());
        if (!missing.isEmpty()) for (FuelReceipt r : receipts.findActiveByIds(tenantId, missing)) if (r.getReversedAt() == null) receiptById.put(r.getId(), r);

        Map<UUID, List<FuelDispatchReceiptMatch>> byDispatch = new HashMap<>();
        Set<UUID> linkedReceipts = new HashSet<>();
        for (FuelDispatchReceiptMatch m : ms) {
            if (!receiptById.containsKey(m.getReceiptId())) continue; // reversed or removed since it was linked
            byDispatch.computeIfAbsent(m.getDispatchId(), k -> new ArrayList<>()).add(m);
            linkedReceipts.add(m.getReceiptId());
        }

        Map<UUID, ForecourtAcc> acc = new LinkedHashMap<>();
        for (FuelDispatch d : ds) {
            ForecourtAcc a = acc.computeIfAbsent(d.getDestinationDepotId(), k -> new ForecourtAcc());
            FuelTank from0 = tankById.get(d.getTankId());
            String fromTank = from0 == null ? "Tank" : from0.getName();
            String fuel = from0 == null ? "" : from0.getFuelType();
            a.dispatched = a.dispatched.add(d.getLitresDispensed());
            List<FuelDispatchReceiptMatch> linked = byDispatch.getOrDefault(d.getId(), List.of());
            if (linked.isEmpty()) { a.open.add(new OpenDispatch(d.getId(), d.getDispatchedAt(), fromTank, fuel, d.getLitresDispensed())); continue; }
            BigDecimal got = BigDecimal.ZERO;
            List<LinkedReceipt> lr = new ArrayList<>();
            for (FuelDispatchReceiptMatch m : linked) {
                FuelReceipt r = receiptById.get(m.getReceiptId());
                got = got.add(r.getLitresReceived());
                lr.add(new LinkedReceipt(m.getId(), r.getId(), r.getReceivedAt(), r.getLitresReceived(), r.getDeliveryNote()));
            }
            a.received = a.received.add(got);
            a.matched.add(new Matched(d.getId(), d.getDispatchedAt(), fromTank, fuel, d.getLitresDispensed(), got, d.getLitresDispensed().subtract(got), lr));
        }
        for (Map.Entry<UUID, List<FuelReceipt>> e : receiptsByDepot.entrySet()) {
            ForecourtAcc a = acc.computeIfAbsent(e.getKey(), k -> new ForecourtAcc());
            for (FuelReceipt r : e.getValue()) {
                if (linkedReceipts.contains(r.getId())) continue;
                FuelTank t = tankById.get(r.getTankId());
                a.openReceipts.add(new OpenReceipt(r.getId(), r.getReceivedAt(), t == null ? "Tank" : t.getName(), t == null ? "" : t.getFuelType(), r.getLitresReceived(), r.getDeliveryNote()));
            }
        }
        List<ForecourtMatching> out = new ArrayList<>();
        for (Map.Entry<UUID, ForecourtAcc> e : acc.entrySet()) {
            FuelDepot fd = depotById.get(e.getKey());
            if (fd == null) continue;
            ForecourtAcc a = e.getValue();
            out.add(new ForecourtMatching(e.getKey(), fd.getName(), a.matched, a.open, a.openReceipts, a.dispatched, a.received));
        }
        out.sort(Comparator.comparing(ForecourtMatching::name));
        return new MatchingReport(from, to, out);
    }

    /** Link a receipt to the dispatch it came from. {@code allowedDepotIds} null means no depot limit. */
    @Transactional
    public void match(TenantId tenantId, UUID dispatchId, UUID receiptId, UUID by, Set<UUID> allowedDepotIds) {
        FuelDispatch d = dispatches.findActiveById(tenantId, dispatchId).orElseThrow(() -> new ResourceNotFoundException("Dispatch", dispatchId.toString()));
        FuelReceipt r = receipts.findActiveById(tenantId, receiptId).orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId.toString()));
        if (d.isReversed()) throw new IllegalStateException("That dispatch has been reversed.");
        if (r.getReversedAt() != null) throw new IllegalStateException("That receipt has been reversed.");
        if (d.getDestinationDepotId() == null) throw new IllegalArgumentException("That dispatch has no destination forecourt.");
        FuelTank rt = tanks.findActiveById(tenantId, r.getTankId()).orElseThrow(() -> new ResourceNotFoundException("Tank", r.getTankId().toString()));
        FuelTank dt = tanks.findActiveById(tenantId, d.getTankId()).orElseThrow(() -> new ResourceNotFoundException("Tank", d.getTankId().toString()));
        if (!d.getDestinationDepotId().equals(rt.getDepotId())) throw new IllegalArgumentException("That receipt is not in a tank of the forecourt the dispatch was delivered to.");
        if (allowedDepotIds != null && !allowedDepotIds.contains(d.getDestinationDepotId()))
            throw new HandyFlowException("Your access is limited to other depots, so you cannot match for this forecourt.", HttpStatus.FORBIDDEN, "FUEL_SCOPE_DEPOT");
        if (!Objects.equals(dt.getFuelType(), rt.getFuelType())) throw new IllegalArgumentException("The fuel types differ: " + dt.getFuelType() + " was dispatched and " + rt.getFuelType() + " was received.");
        if (matches.findByReceipt(tenantId, receiptId).isPresent()) throw new IllegalStateException("That receipt is already linked to a dispatch.");
        matches.save(FuelDispatchReceiptMatch.of(tenantId, dispatchId, receiptId, by));
    }

    @Transactional
    public void unmatch(TenantId tenantId, UUID matchId, Set<UUID> allowedDepotIds) {
        FuelDispatchReceiptMatch m = matches.findOne(tenantId, matchId).orElseThrow(() -> new ResourceNotFoundException("Match", matchId.toString()));
        if (allowedDepotIds != null) {
            FuelDispatch d = dispatches.findActiveById(tenantId, m.getDispatchId()).orElse(null);
            if (d == null || d.getDestinationDepotId() == null || !allowedDepotIds.contains(d.getDestinationDepotId()))
                throw new HandyFlowException("Your access is limited to other depots, so you cannot change this match.", HttpStatus.FORBIDDEN, "FUEL_SCOPE_DEPOT");
        }
        matches.delete(m);
    }

    private static final class ForecourtAcc {
        BigDecimal dispatched = BigDecimal.ZERO, received = BigDecimal.ZERO;
        List<Matched> matched = new ArrayList<>();
        List<OpenDispatch> open = new ArrayList<>();
        List<OpenReceipt> openReceipts = new ArrayList<>();
    }
}

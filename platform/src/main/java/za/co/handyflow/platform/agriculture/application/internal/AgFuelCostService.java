package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.repository.AgCostEntryRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgEquipmentFuelRules;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.AllocateFuelRequest;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.FuelDispatchRow;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.FuelOverview;
import za.co.handyflow.platform.fleet.application.FleetFacade;
import za.co.handyflow.platform.fleet.application.FleetFacade.EquipmentSummary;
import za.co.handyflow.platform.fuel.application.FuelFacade;
import za.co.handyflow.platform.fuel.application.FuelFacade.OwnDispatch;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Allocates the tenant's own fuel to production (ADR-001, W4). Fuel owns the dispatch and its cost per litre at the time (the tank's weighted
 * average, snapshotted on the dispatch); Agriculture records only which targets the fuel went to and SNAPSHOTS that cost into the ledger. A
 * dispatch is allocated once, as one group split across targets, and reversing the group frees it. Dispatches to customers are sales, not own
 * consumption, and never reach this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgFuelCostService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_ROWS = 400;

    private final FuelFacade fuelFacade;
    private final FleetFacade fleetFacade;
    private final AgFarmRepository farmRepository;
    private final AgCostEntryRepository costEntryRepository;
    private final AgCostEntryService costEntryService;

    /** Own-fuel dispatches in the range (default the last 30 days) that are not allocated yet. */
    @Transactional(readOnly = true)
    public FuelOverview unallocated(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to) {
        requireFarm(tenantId, farmId);
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS);
        if (start.isAfter(end)) throw new IllegalArgumentException("from cannot be after to");
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) throw new IllegalArgumentException("choose a range of at most " + MAX_RANGE_DAYS + " days");

        List<OwnDispatch> dispatches = fuelFacade.findOwnDispatches(tenantId, start.atStartOfDay(SAST).toInstant(), end.plusDays(1).atStartOfDay(SAST).toInstant(), MAX_ROWS);
        Set<UUID> done = new HashSet<>();
        if (!dispatches.isEmpty()) done.addAll(costEntryRepository.findActiveSourceRefs(tenantId, AgEquipmentFuelRules.FUEL_SOURCE, dispatches.stream().map(OwnDispatch::id).toList()));

        Map<UUID, String> labels = new HashMap<>();
        List<FuelDispatchRow> rows = new ArrayList<>();
        for (OwnDispatch d : dispatches) {
            if (done.contains(d.id())) continue;
            BigDecimal cost = d.costPerLitre() == null || d.costPerLitre().signum() <= 0 || d.litres() == null ? null : AgEquipmentFuelRules.fuelAmount(d.litres(), AgEquipmentFuelRules.snapshotRate(d.costPerLitre()));
            rows.add(new FuelDispatchRow(d.id(), d.dispatchedAt().atZone(SAST).toLocalDate(), d.tankName(), d.vehicleId(), vehicleLabel(tenantId, d, labels), d.recipientName(),
                    d.litres(), d.costPerLitre(), cost, d.hoursReading()));
        }
        return new FuelOverview(start, end, rows, done.size());
    }

    /** Allocates one dispatch's whole cost across targets on the farm. */
    @Transactional
    public List<CostEntryResponse> allocate(TenantId tenantId, UUID farmId, UUID userId, AllocateFuelRequest req) {
        requireFarm(tenantId, farmId);
        OwnDispatch d = fuelFacade.findOwnDispatch(tenantId, req.dispatchId()).orElseThrow(() -> new ResourceNotFoundException("FuelDispatch", req.dispatchId().toString()));
        if (costEntryRepository.countActiveBySource(tenantId, AgEquipmentFuelRules.FUEL_SOURCE, d.id()) > 0) {
            throw new IllegalArgumentException("this fuel is already allocated; reverse its ledger entry first to allocate it again");
        }
        if (d.litres() == null || d.litres().signum() <= 0) throw new IllegalArgumentException("this dispatch has no litres recorded");
        if (d.costPerLitre() == null || d.costPerLitre().signum() <= 0) throw new IllegalArgumentException("Fuel recorded no cost per litre for this dispatch, so it cannot be costed");

        BigDecimal rate = AgEquipmentFuelRules.snapshotRate(d.costPerLitre());
        BigDecimal amount = AgEquipmentFuelRules.fuelAmount(d.litres(), rate);
        if (amount.signum() <= 0) throw new IllegalArgumentException("the cost rounds to nothing");

        LocalDate date = d.dispatchedAt().atZone(SAST).toLocalDate();
        String label = vehicleLabel(tenantId, d, new HashMap<>());
        List<CostEntryResponse> out = costEntryService.record(tenantId, farmId, userId, date, "FUEL",
                "Fuel: " + d.litres().stripTrailingZeros().toPlainString() + " L" + (d.tankName() != null ? " from " + d.tankName() : "") + " to " + label,
                AgEquipmentFuelRules.FUEL_SOURCE, d.id(), d.litres(), "L", rate, amount, req.notes(), AgAllocationShares.of(req.allocations()));
        log.info("Fuel allocated dispatch={} litres={} amount={} tenant={} farm={}", d.id(), d.litres(), amount, tenantId.getValue(), farmId);
        return out;
    }

    private String vehicleLabel(TenantId tenantId, OwnDispatch d, Map<UUID, String> cache) {
        if (d.vehicleId() == null) return d.recipientName() != null && !d.recipientName().isBlank() ? d.recipientName() : "an asset";
        return cache.computeIfAbsent(d.vehicleId(), id -> fleetFacade.findEquipment(tenantId, id).map(AgFuelCostService::describe).orElse("a vehicle"));
    }

    private static String describe(EquipmentSummary m) {
        String kind = ((m.make() == null ? "" : m.make()) + " " + (m.model() == null ? "" : m.model())).trim();
        String reg = m.registration() == null ? "Vehicle" : m.registration();
        return kind.isEmpty() ? reg : reg + " (" + kind + ")";
    }

    private void requireFarm(TenantId tenantId, UUID farmId) {
        farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
    }
}

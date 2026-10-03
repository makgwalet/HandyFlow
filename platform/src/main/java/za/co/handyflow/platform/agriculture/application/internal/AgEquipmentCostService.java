package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgEquipmentFuelRules;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.CostEquipmentRequest;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.EquipmentOption;
import za.co.handyflow.platform.fleet.application.FleetFacade;
import za.co.handyflow.platform.fleet.application.FleetFacade.EquipmentSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Costs a machine's use on the farm into the cost ledger (ADR-001, W4). Fleet owns the machine and its operating rate per hour (service and
 * repairs only, never fuel and never depreciation); Agriculture records only which crop cycle, group, animal or enterprise the hours went to, and
 * SNAPSHOTS the rate into the ledger entry so a later change in Fleet never rewrites a past cost. Fuel is allocated separately, from fuel
 * dispatches, so it is never counted twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgEquipmentCostService {

    private final FleetFacade fleetFacade;
    private final AgFarmRepository farmRepository;
    private final AgCostEntryService costEntryService;

    /** The tenant's machines with the rate each would be costed at. */
    @Transactional(readOnly = true)
    public List<EquipmentOption> equipment(TenantId tenantId) {
        return fleetFacade.listEquipment(tenantId).stream().map(AgEquipmentCostService::toOption).toList();
    }

    /** Costs one day's use of a machine, split across the targets it worked on. */
    @Transactional
    public List<CostEntryResponse> costUse(TenantId tenantId, UUID farmId, UUID userId, CostEquipmentRequest req) {
        farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
        AgEquipmentFuelRules.requireHoursOfUse(req.hours());
        EquipmentSummary machine = fleetFacade.findEquipment(tenantId, req.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", req.vehicleId().toString()));
        if (machine.operatingRatePerHour() == null || machine.operatingRatePerHour().signum() <= 0) {
            throw new IllegalArgumentException("no operating rate is set for " + machine.registration() + "; set it in Fleet first (service and repairs per hour, not fuel)");
        }
        BigDecimal rate = AgEquipmentFuelRules.snapshotRate(machine.operatingRatePerHour());
        BigDecimal amount = AgEquipmentFuelRules.equipmentAmount(req.hours(), rate);
        if (amount.signum() <= 0) throw new IllegalArgumentException("the cost rounds to nothing; check the hours and the machine's rate");

        List<CostEntryResponse> out = costEntryService.record(tenantId, farmId, userId, req.workDate(), "EQUIPMENT",
                "Equipment: " + describe(machine) + ", " + req.hours().stripTrailingZeros().toPlainString() + " h",
                AgEquipmentFuelRules.EQUIPMENT_SOURCE, machine.id(), req.hours(), "h", rate, amount, req.notes(), AgAllocationShares.of(req.allocations()));
        log.info("Equipment use costed vehicle={} hours={} amount={} tenant={} farm={}", machine.id(), req.hours(), amount, tenantId.getValue(), farmId);
        return out;
    }

    private static EquipmentOption toOption(EquipmentSummary m) {
        return new EquipmentOption(m.id(), m.registration(), describe(m), m.engineHours(), m.operatingRatePerHour());
    }

    private static String describe(EquipmentSummary m) {
        StringBuilder sb = new StringBuilder(m.registration() == null ? "Vehicle" : m.registration());
        String kind = ((m.make() == null ? "" : m.make()) + " " + (m.model() == null ? "" : m.model())).trim();
        if (!kind.isEmpty()) sb.append(" (").append(kind).append(")");
        return sb.toString();
    }
}

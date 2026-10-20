package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.application.FleetFacade;
import za.co.handyflow.platform.fleet.domain.model.AssetProfile;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.AssetProfileRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class FleetFacadeImpl implements FleetFacade {

    private static final int MAX_VEHICLES = 500;

    private final VehicleRepository vehicleRepository;
    private final AssetProfileRepository profileRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipmentSummary> findEquipment(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId).map(v -> toSummary(v,
                profileRepository.findByTenantIdAndVehicleId(tenantId, v.getId())
                        .map(AssetProfile::getOperatingRatePerHour).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentSummary> listEquipment(TenantId tenantId) {
        List<Vehicle> vehicles = vehicleRepository.findAllActive(tenantId, PageRequest.of(0, MAX_VEHICLES)).getContent().stream()
                .filter(v -> v.getStatus() != VehicleStatus.RETIRED)
                .toList();
        if (vehicles.isEmpty()) return List.of();
        // The hourly rate lives on the asset profile; one query for all of them.
        Map<UUID, BigDecimal> rates = profileRepository
                .findByTenantIdAndVehicleIdIn(tenantId, vehicles.stream().map(Vehicle::getId).toList()).stream()
                .filter(p -> p.getOperatingRatePerHour() != null)
                .collect(Collectors.toMap(AssetProfile::getVehicleId, AssetProfile::getOperatingRatePerHour, (x, y) -> x));
        return vehicles.stream()
                .map(v -> toSummary(v, rates.get(v.getId())))
                .sorted(Comparator.comparing(EquipmentSummary::registration, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private static EquipmentSummary toSummary(Vehicle v, BigDecimal ratePerHour) {
        return new EquipmentSummary(v.getId(), v.getRegistration(), v.getMake(), v.getModel(), v.getVehicleType(), v.getEngineHours(), ratePerHour);
    }
}

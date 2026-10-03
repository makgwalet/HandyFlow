package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.application.FleetFacade;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class FleetFacadeImpl implements FleetFacade {

    private static final int MAX_VEHICLES = 500;

    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipmentSummary> findEquipment(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId).map(FleetFacadeImpl::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentSummary> listEquipment(TenantId tenantId) {
        return vehicleRepository.findAllActive(tenantId, PageRequest.of(0, MAX_VEHICLES)).getContent().stream()
                .filter(v -> v.getStatus() != VehicleStatus.RETIRED)
                .map(FleetFacadeImpl::toSummary)
                .sorted(Comparator.comparing(EquipmentSummary::registration, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private static EquipmentSummary toSummary(Vehicle v) {
        return new EquipmentSummary(v.getId(), v.getRegistration(), v.getMake(), v.getModel(), v.getVehicleType(), v.getEngineHours(), v.getOperatingRatePerHour());
    }
}

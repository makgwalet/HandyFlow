package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.fuel.FleetVehicleLookup;
import za.co.handyflow.platform.fuel.FleetVehicleLookup.VehicleRef;
import za.co.handyflow.platform.fuel.dto.FleetVehicleOption;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Links a truck stop stay or tab to one of the company's own Fleet vehicles. Fleet owns the vehicle and its registration: a linked record takes
 * Fleet's registration. A vehicle chosen explicitly must be one of this company's; when none is chosen, a registration that matches a Fleet
 * vehicle exactly (ignoring case and spaces) is linked automatically. Fleet being absent or slow never stops a booking: the record is simply not linked.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelTruckStopFleetLinks {

    public record Link(UUID vehicleId, String registration) {}

    private final ObjectProvider<FleetVehicleLookup> fleet;

    static String normalise(String reg) { return reg == null ? "" : reg.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }

    /** An explicit choice that cannot be honoured is an error; an automatic match that cannot be made is just no link. */
    public Optional<Link> resolve(TenantId tenant, UUID chosenVehicleId, String typedRegistration) {
        FleetVehicleLookup lookup = fleet == null ? null : fleet.getIfAvailable();
        if (chosenVehicleId != null) {
            if (lookup == null) throw new IllegalStateException("Fleet is not available, so a vehicle cannot be linked.");
            VehicleRef v = lookup.find(tenant, chosenVehicleId).orElseThrow(() -> new IllegalArgumentException("That vehicle is not in your Fleet."));
            return Optional.of(new Link(v.id(), v.registration().trim().toUpperCase(Locale.ROOT)));
        }
        String key = normalise(typedRegistration);
        if (lookup == null || key.isEmpty()) return Optional.empty();
        try {
            List<VehicleRef> matches = lookup.list(tenant).stream().filter(v -> normalise(v.registration()).equals(key)).toList();
            // More than one match is ambiguous: leave it unlinked rather than guess.
            return matches.size() == 1 ? Optional.of(new Link(matches.get(0).id(), matches.get(0).registration().trim().toUpperCase(Locale.ROOT))) : Optional.empty();
        } catch (RuntimeException e) {
            log.info("Fleet lookup failed, record left unlinked: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** The company's Fleet vehicles to choose from; empty when Fleet is not available. */
    public List<FleetVehicleOption> options(TenantId tenant) {
        FleetVehicleLookup lookup = fleet == null ? null : fleet.getIfAvailable();
        if (lookup == null) return List.of();
        return lookup.list(tenant).stream().map(v -> new FleetVehicleOption(v.id(), v.registration(), v.label(), null)).toList();
    }
}

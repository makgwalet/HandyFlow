package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.FleetFuelLogRow;
import za.co.handyflow.platform.fleet.dto.FleetFuelLogSummary;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The fleet-wide fuel log: every fill-up across all vehicles, searchable and paged, with totals for the same filter. */
@Service
@RequiredArgsConstructor
public class FuelLogService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    /** A vehicle id that matches nothing, passed when "all vehicles" is on (the query never reads it then). */
    private static final UUID NO_VEHICLE = new UUID(0L, 0L);

    private final FuelFillupRepository fuelFillupRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public Page<FleetFuelLogRow> log(TenantId tenantId, LocalDate from, LocalDate to, UUID vehicleId, String q, Pageable pageable) {
        FuelLogFilter.Period p = FuelLogFilter.period(from, to, LocalDate.now(SAST));
        Page<FuelFillup> page = fuelFillupRepository.searchLog(tenantId.getValue(), tenantId, p.from(), p.to(),
                vehicleId == null, vehicleId == null ? NO_VEHICLE : vehicleId, FuelLogFilter.pattern(q), pageable);
        Map<UUID, String> registrations = new HashMap<>();
        for (Vehicle v : vehicleRepository.findAllById(page.getContent().stream().map(FuelFillup::getVehicleId).distinct().toList())) {
            registrations.put(v.getId(), v.getRegistration());
        }
        return page.map(f -> new FleetFuelLogRow(f.getId(), f.getVehicleId(), registrations.getOrDefault(f.getVehicleId(), "Unknown vehicle"),
                f.getFilledAt(), f.getLitres(), f.getPricePerLitre(), f.getTotalCost(), f.getOdometerAtFillup(),
                f.getStation(), f.getReceiptRef(), f.isFullTank()));
    }

    @Transactional(readOnly = true)
    public FleetFuelLogSummary summary(TenantId tenantId, LocalDate from, LocalDate to, UUID vehicleId, String q) {
        FuelLogFilter.Period p = FuelLogFilter.period(from, to, LocalDate.now(SAST));
        List<Object[]> rows = fuelFillupRepository.totalsFor(tenantId.getValue(), tenantId, p.from(), p.to(),
                vehicleId == null, vehicleId == null ? NO_VEHICLE : vehicleId, FuelLogFilter.pattern(q));
        Object[] r = rows.isEmpty() ? new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO} : rows.get(0);
        long count = ((Number) r[0]).longValue();
        BigDecimal litres = new BigDecimal(String.valueOf(r[1])), spend = new BigDecimal(String.valueOf(r[2]));
        BigDecimal avg = litres.signum() == 0 ? BigDecimal.ZERO : spend.divide(litres, 2, RoundingMode.HALF_UP);
        return new FleetFuelLogSummary(count, litres, spend, avg);
    }
}

package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.DipReading;
import za.co.handyflow.platform.fuel.domain.model.FuelStockMovement;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.DipReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelStockMovementRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.FuelRetailDtos.MovementLine;
import za.co.handyflow.platform.fuel.dto.FuelRetailDtos.TankReconciliation;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * D01: tank-to-pump reconciliation. Between the first and last dip in a window, opening dip + everything the system recorded
 * should equal the closing dip; the difference is the loss (or gain) nothing else explains. Separately, the pump meters' litres
 * for the window are compared with the ledger's forecourt sales for the same window, which should agree.
 * No tolerance is built in: the caller supplies one if they want a flag, because what counts as acceptable is the owner's call.
 */
@Service
@RequiredArgsConstructor
public class FuelTankReconciliationService {

    static final String FORECOURT = "FORECOURT_SHIFT";

    private final FuelTankRepository tanks;
    private final DipReadingRepository dips;
    private final FuelStockMovementRepository movements;
    private final FuelShiftReadingRepository readings;

    @Transactional(readOnly = true)
    public TankReconciliation reconcile(TenantId tenantId, UUID tankId, Instant from, Instant to, BigDecimal tolerancePct) {
        FuelTank tank = tanks.findActiveById(tenantId, tankId).orElseThrow(() -> new ResourceNotFoundException("Tank", tankId.toString()));
        if (tolerancePct != null && (tolerancePct.signum() < 0 || tolerancePct.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("The tolerance must be between 0% and 100%.");
        }
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(Duration.ofDays(30));
        if (!start.isBefore(end)) throw new IllegalArgumentException("The start of the window must be before the end.");
        List<DipReading> inWindow = dips.findByTankAndReadAtBetween(tenantId, tankId, start, end);
        if (inWindow.size() < 2) {
            return TankReconciliation.notReady(tankId, tank.getName(), tank.getFuelType(),
                    "Needs at least two dip readings in this period; found " + inWindow.size() + ". Record a dip at the start and end of the period.");
        }
        DipReading first = inWindow.get(0), last = inWindow.get(inWindow.size() - 1);
        List<FuelStockMovement> moved = movements.findByTankBetween(tenantId, tankId, first.getReadAt(), last.getReadAt());
        return compute(tank, first, last, moved,
                readings.sumMeterLitresForTank(tenantId, tankId, first.getReadAt(), last.getReadAt()), tolerancePct);
    }

    /** Pure arithmetic, separated so it can be tested without a database. */
    static TankReconciliation compute(FuelTank tank, DipReading first, DipReading last, List<FuelStockMovement> moved,
                                      BigDecimal meterLitres, BigDecimal tolerancePct) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        BigDecimal net = BigDecimal.ZERO, outflow = BigDecimal.ZERO, forecourt = BigDecimal.ZERO;
        for (FuelStockMovement m : moved) {
            String label = label(m);
            sums.merge(label, m.getLitresDelta(), BigDecimal::add);
            counts.merge(label, 1, Integer::sum);
            net = net.add(m.getLitresDelta());
            if (m.getLitresDelta().signum() < 0) outflow = outflow.add(m.getLitresDelta().negate());
            if (FORECOURT.equals(m.getSourceType())) forecourt = forecourt.add(m.getLitresDelta());
        }
        List<MovementLine> lines = new ArrayList<>();
        sums.forEach((k, v) -> lines.add(new MovementLine(k, v, counts.get(k))));
        BigDecimal expected = first.getActualLitres().add(net);
        BigDecimal variance = last.getActualLitres().subtract(expected);
        BigDecimal pct = outflow.signum() == 0 ? null : variance.multiply(new BigDecimal("100")).divide(outflow, 2, RoundingMode.HALF_UP);
        BigDecimal meters = meterLitres == null ? BigDecimal.ZERO : meterLitres;
        BigDecimal ledgerForecourt = forecourt.negate();   // sales are negative movements; show litres sold as positive
        boolean outside = tolerancePct != null && pct != null && pct.abs().compareTo(tolerancePct) > 0;
        return new TankReconciliation(true, null, tank.getId(), tank.getName(), tank.getFuelType(), first.getReadAt(), first.getActualLitres(),
                last.getReadAt(), last.getActualLitres(), lines, expected, variance, outflow, pct, meters, ledgerForecourt,
                meters.subtract(ledgerForecourt), tolerancePct, outside);
    }

    private static String label(FuelStockMovement m) {
        if (FORECOURT.equals(m.getSourceType())) return m.getMovementType() == FuelStockMovement.Type.REVERSAL ? "Forecourt sales reversed" : "Forecourt sales (pump meters)";
        return switch (m.getMovementType()) {
            case OPENING -> "Opening balance";
            case RECEIPT -> "Supplier receipts";
            case DISPATCH -> "Dispatches";
            case DELIVERY -> "Customer deliveries";
            case ADJUSTMENT -> "Stock adjustments";
            case TRANSFER_OUT -> "Transfers out";
            case TRANSFER_IN -> "Transfers in";
            case REVERSAL -> "Reversals";
        };
    }
}

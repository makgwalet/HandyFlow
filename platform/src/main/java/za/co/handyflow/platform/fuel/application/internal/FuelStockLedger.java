package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.fuel.domain.model.FuelReservation;
import za.co.handyflow.platform.fuel.domain.model.FuelStockMovement;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelReservationRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelStockMovementRepository;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The one place that writes stock-ledger lines and enforces reservations.
 * Every change to a tank's balance must be followed by post() in the same
 * transaction, so SUM(ledger) always equals the tank balance.
 */
@Component
@RequiredArgsConstructor
public class FuelStockLedger {

    private final FuelStockMovementRepository movementRepository;
    private final FuelReservationRepository reservationRepository;

    /** Records the movement that just happened to {@code tank} (call AFTER the tank was changed). */
    public FuelStockMovement post(FuelTank tank, FuelStockMovement.Type type, BigDecimal delta,
                                  String sourceType, UUID sourceId, String reason) {
        return movementRepository.save(FuelStockMovement.of(
                tank.getTenantId(), tank.getId(), type, delta, tank.getCurrentLitres(),
                sourceType, sourceId, reason, currentUserOrNull()));
    }

    /** Litres held by live reservations on the tank. */
    public BigDecimal reserved(TenantId tenantId, UUID tankId) {
        BigDecimal r = reservationRepository.sumLiveForTank(tenantId, tankId, Instant.now());
        return r == null ? BigDecimal.ZERO : r;
    }

    /**
     * Refuses to take fuel that is reserved for someone else. {@code ownReserved} is
     * what the caller itself reserved (a delivery completing its own reservation).
     * Tenants with no reservations are unaffected.
     */
    public void requireUnreserved(FuelTank tank, BigDecimal litres, BigDecimal ownReserved) {
        BigDecimal reservedForOthers = reserved(tank.getTenantId(), tank.getId()).subtract(ownReserved);
        if (reservedForOthers.signum() <= 0) return;
        BigDecimal free = tank.getCurrentLitres().subtract(reservedForOthers);
        if (litres.compareTo(free) > 0) {
            throw new IllegalStateException("Only " + free.max(BigDecimal.ZERO).toPlainString()
                    + "L of " + tank.getName() + " is free: " + reservedForOthers.toPlainString()
                    + "L is reserved for other customers or orders.");
        }
    }

    /** Active reservations a specific delivery or order made on this tank. */
    public List<FuelReservation> ownReservations(TenantId tenantId, UUID tankId, String refType, UUID refId) {
        return reservationRepository.findActiveByReference(tenantId, tankId, refType, refId);
    }

    public BigDecimal total(List<FuelReservation> list) {
        return list.stream().map(FuelReservation::getLitres).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Marks the delivery's own reservations as used up once it has taken the fuel. */
    public void consume(List<FuelReservation> own) {
        own.forEach(FuelReservation::consume);
        reservationRepository.saveAll(own);
    }

    private static UUID currentUserOrNull() {
        try { return TenantContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}

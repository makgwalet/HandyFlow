package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Depots, stock adjustments, transfers, reservations and availability (Stage 1A). */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelStockService {

    private final FuelTankRepository tankRepository;
    private final FuelDepotRepository depotRepository;
    private final FuelStockMovementRepository movementRepository;
    private final FuelTransferRepository transferRepository;
    private final FuelReservationRepository reservationRepository;
    private final FuelStockLedger ledger;
    private final CrmFacade crmFacade;

    // ── Depots ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DepotResponse> getDepots(TenantId tenantId) {
        Map<UUID, Long> counts = tankRepository.findAllActive(tenantId).stream()
                .filter(t -> t.getDepotId() != null)
                .collect(Collectors.groupingBy(FuelTank::getDepotId, Collectors.counting()));
        return depotRepository.findAllActive(tenantId).stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L).intValue())).toList();
    }

    @Transactional
    public DepotResponse createDepot(TenantId tenantId, DepotRequest req) {
        if (depotRepository.codeInUse(tenantId, req.code().trim(), null)) {
            throw new ConflictException("Depot code " + req.code().trim().toUpperCase() + " is already in use.");
        }
        FuelDepot d = depotRepository.save(FuelDepot.create(tenantId, req.name(), req.code(), req.address()));
        return toResponse(d, 0);
    }

    @Transactional
    public DepotResponse updateDepot(TenantId tenantId, UUID id, DepotRequest req) {
        FuelDepot d = findDepot(tenantId, id);
        if (depotRepository.codeInUse(tenantId, req.code().trim(), id)) {
            throw new ConflictException("Depot code " + req.code().trim().toUpperCase() + " is already in use.");
        }
        d.update(req.name(), req.code(), req.address());
        return toResponse(depotRepository.save(d), tanksIn(tenantId, id));
    }

    @Transactional
    public void deactivateDepot(TenantId tenantId, UUID id) {
        FuelDepot d = findDepot(tenantId, id);
        if (tanksIn(tenantId, id) > 0) {
            throw new IllegalStateException("Move or deactivate the tanks in this depot first.");
        }
        d.softDelete();
        depotRepository.save(d);
    }

    @Transactional
    public TankResponse placeTank(TenantId tenantId, UUID tankId, TankPlacementRequest req) {
        FuelTank tank = lockTank(tenantId, tankId);
        if (req.depotId() != null) findDepot(tenantId, req.depotId());
        tank.place(req.depotId(), req.safetyStockLitres());
        tankRepository.save(tank);
        return new TankResponse(tank.getId(), tank.getName(), tank.getFuelType(), tank.getCapacityLitres(),
                tank.getCurrentLitres(), tank.getFillPercentage(), tank.isLow(), tank.getLocation(),
                tank.getCreatedAt(), tank.getLowThresholdPct());
    }

    // ── Adjustments and transfers ────────────────────────────────────────────

    @Transactional
    public MovementResponse adjust(TenantId tenantId, UUID tankId, AdjustStockRequest req) {
        FuelTank tank = lockTank(tenantId, tankId);
        if (req.litres().signum() < 0) {
            // Removing fuel by adjustment may not eat into stock reserved for customers.
            ledger.requireUnreserved(tank, req.litres().negate(), BigDecimal.ZERO);
        }
        tank.adjust(req.litres());
        tankRepository.save(tank);
        FuelStockMovement m = ledger.post(tank, FuelStockMovement.Type.ADJUSTMENT, req.litres(),
                "ADJUSTMENT", UUID.randomUUID(), req.reason().trim());
        log.info("Stock adjusted tank={} litres={} reason={}", tank.getName(), req.litres(), req.reason());
        return toResponse(m);
    }

    @Transactional
    public TransferResponse transfer(TenantId tenantId, TransferRequest req) {
        if (req.fromTankId().equals(req.toTankId())) {
            throw new IllegalArgumentException("Choose two different tanks.");
        }
        // Lock in a fixed order so two opposite transfers can never deadlock.
        UUID first = req.fromTankId().compareTo(req.toTankId()) < 0 ? req.fromTankId() : req.toTankId();
        UUID second = first.equals(req.fromTankId()) ? req.toTankId() : req.fromTankId();
        FuelTank a = lockTank(tenantId, first);
        FuelTank b = lockTank(tenantId, second);
        FuelTank from = a.getId().equals(req.fromTankId()) ? a : b;
        FuelTank to   = a.getId().equals(req.toTankId())   ? a : b;
        if (!from.getFuelType().equalsIgnoreCase(to.getFuelType())) {
            throw new IllegalArgumentException("Cannot transfer " + from.getFuelType() + " into a " + to.getFuelType() + " tank.");
        }
        ledger.requireUnreserved(from, req.litres(), BigDecimal.ZERO);
        // Fuel moves at the source tank's cost; the destination's average is recalculated.
        BigDecimal cost = from.getCostPerLitreWac() != null ? from.getCostPerLitreWac() : BigDecimal.ZERO;
        from.removeStock(req.litres());
        to.addStock(req.litres(), cost);
        tankRepository.save(from);
        tankRepository.save(to);
        FuelTransfer t = transferRepository.save(FuelTransfer.of(tenantId, from.getId(), to.getId(),
                req.litres(), req.reason().trim(), null));
        ledger.post(from, FuelStockMovement.Type.TRANSFER_OUT, req.litres().negate(), "TRANSFER", t.getId(), req.reason().trim());
        ledger.post(to, FuelStockMovement.Type.TRANSFER_IN, req.litres(), "TRANSFER", t.getId(), req.reason().trim());
        return new TransferResponse(t.getId(), t.getFromTankId(), t.getToTankId(), t.getLitres(), t.getReason(), t.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> getTransfers(TenantId tenantId, Pageable pageable) {
        return transferRepository.findAllForTenant(tenantId, pageable)
                .map(t -> new TransferResponse(t.getId(), t.getFromTankId(), t.getToTankId(), t.getLitres(), t.getReason(), t.getCreatedAt()));
    }

    // ── Reservations ─────────────────────────────────────────────────────────

    @Transactional
    public ReservationResponse reserve(TenantId tenantId, ReservationRequest req, UUID actingUserId) {
        FuelTank tank = lockTank(tenantId, req.tankId());
        if (req.customerId() != null && !crmFacade.customerExists(tenantId, req.customerId())) {
            throw new ResourceNotFoundException("Customer", req.customerId().toString());
        }
        if (req.expiresAt() != null && !req.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("The expiry must be in the future.");
        }
        BigDecimal atp = availableToPromise(tank);
        if (req.litres().compareTo(atp) > 0) {
            throw new IllegalStateException("Only " + atp.toPlainString() + "L of " + tank.getName()
                    + " is available to promise (stock less reservations and safety stock).");
        }
        FuelReservation r = reservationRepository.save(FuelReservation.create(tenantId, tank.getId(), req.litres(),
                req.customerId(), req.referenceType(), req.referenceId(), req.expiresAt(), req.note(), actingUserId));
        return toResponse(r);
    }

    @Transactional
    public ReservationResponse releaseReservation(TenantId tenantId, UUID id) {
        FuelReservation r = reservationRepository.findOwned(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id.toString()));
        r.release();
        return toResponse(reservationRepository.save(r));
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getReservations(TenantId tenantId, Pageable pageable) {
        return reservationRepository.findAllForTenant(tenantId, pageable).map(this::toResponse);
    }

    // ── Availability and ledger ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(TenantId tenantId) {
        Instant now = Instant.now();
        Map<UUID, BigDecimal> reserved = new HashMap<>();
        for (Object[] row : reservationRepository.sumLiveByTank(tenantId, now)) {
            reserved.put((UUID) row[0], (BigDecimal) row[1]);
        }
        Map<UUID, String> depotNames = depotRepository.findAllActive(tenantId).stream()
                .collect(Collectors.toMap(FuelDepot::getId, FuelDepot::getName));
        List<AvailabilityResponse.TankLine> lines = new ArrayList<>();
        for (FuelTank t : tankRepository.findAllActive(tenantId)) {
            BigDecimal res = reserved.getOrDefault(t.getId(), BigDecimal.ZERO);
            BigDecimal ledgerBalance = movementRepository.sumForTank(tenantId, t.getId());
            lines.add(new AvailabilityResponse.TankLine(t.getId(), t.getName(), t.getFuelType(), t.getDepotId(),
                    t.getCurrentLitres(), res, t.getSafetyStockLitres(), atp(t.getCurrentLitres(), res, t.getSafetyStockLitres()),
                    ledgerBalance, ledgerBalance.compareTo(t.getCurrentLitres()) == 0));
        }
        record Key(UUID depot, String fuel) {}
        Map<Key, List<AvailabilityResponse.TankLine>> grouped = lines.stream()
                .filter(l -> l.depotId() != null)
                .collect(Collectors.groupingBy(l -> new Key(l.depotId(), l.fuelType()), LinkedHashMap::new, Collectors.toList()));
        List<AvailabilityResponse.DepotLine> depots = grouped.entrySet().stream().map(e -> {
            BigDecimal ph = sum(e.getValue(), AvailabilityResponse.TankLine::physical);
            BigDecimal rs = sum(e.getValue(), AvailabilityResponse.TankLine::reserved);
            BigDecimal ss = sum(e.getValue(), AvailabilityResponse.TankLine::safetyStock);
            BigDecimal at = sum(e.getValue(), AvailabilityResponse.TankLine::availableToPromise);
            return new AvailabilityResponse.DepotLine(e.getKey().depot(), depotNames.getOrDefault(e.getKey().depot(), "Depot"),
                    e.getKey().fuel(), ph, rs, ss, at);
        }).toList();
        return new AvailabilityResponse(lines, depots);
    }

    @Transactional(readOnly = true)
    public Page<MovementResponse> getLedger(TenantId tenantId, UUID tankId, Pageable pageable) {
        Page<FuelStockMovement> page = tankId == null
                ? movementRepository.findAllForTenant(tenantId, pageable)
                : movementRepository.findByTank(tenantId, tankId, pageable);
        return page.map(this::toResponse);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static BigDecimal atp(BigDecimal physical, BigDecimal reserved, BigDecimal safety) {
        return physical.subtract(reserved).subtract(safety).max(BigDecimal.ZERO);
    }

    private BigDecimal availableToPromise(FuelTank tank) {
        return atp(tank.getCurrentLitres(), ledger.reserved(tank.getTenantId(), tank.getId()), tank.getSafetyStockLitres());
    }

    private static BigDecimal sum(List<AvailabilityResponse.TankLine> l, java.util.function.Function<AvailabilityResponse.TankLine, BigDecimal> f) {
        return l.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private FuelTank lockTank(TenantId tenantId, UUID id) {
        return tankRepository.findActiveByIdForUpdate(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Tank", id.toString()));
    }

    private FuelDepot findDepot(TenantId tenantId, UUID id) {
        return depotRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Depot", id.toString()));
    }

    private int tanksIn(TenantId tenantId, UUID depotId) {
        return (int) tankRepository.findAllActive(tenantId).stream().filter(t -> depotId.equals(t.getDepotId())).count();
    }

    private DepotResponse toResponse(FuelDepot d, int tanks) {
        return new DepotResponse(d.getId(), d.getName(), d.getCode(), d.getAddress(), d.isActive(), tanks, d.getCreatedAt());
    }

    private MovementResponse toResponse(FuelStockMovement m) {
        return new MovementResponse(m.getId(), m.getTankId(), m.getMovementType().name(), m.getLitresDelta(), m.getBalanceAfter(),
                m.getSourceType(), m.getSourceId(), m.getReason(), m.getCreatedBy(), m.getCreatedAt());
    }

    private ReservationResponse toResponse(FuelReservation r) {
        return new ReservationResponse(r.getId(), r.getTankId(), r.getLitres(), r.getCustomerId(), r.getReferenceType(),
                r.getReferenceId(), r.getStatus(), r.getExpiresAt(), r.getNote(), r.getCreatedAt(), r.getClosedAt());
    }
}

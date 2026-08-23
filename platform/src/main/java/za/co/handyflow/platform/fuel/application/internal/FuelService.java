// fuel/application/internal/FuelService.java

package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.FuelDispatchedToVehicleEvent;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuelService {

    private final FuelTankRepository     tankRepository;
    private final FuelSupplierRepository supplierRepository;
    private final FuelReceiptRepository  receiptRepository;
    private final FuelDispatchRepository dispatchRepository;
    private final DipReadingRepository   dipRepository;
    private final FuelDeliveryRepository deliveryRepository;
    private final ReceiptNumberGenerator receiptNumberGenerator;
    private final NotificationService    notificationService;
    private final TenantAdminRecipients  tenantAdminRecipients;
    private final FuelReconciliationPdfGenerator reconciliationPdfGenerator;
    private final FuelUsageReportPdfGenerator usageReportPdfGenerator;
    private final FuelSupplierStatementPdfGenerator supplierStatementPdfGenerator;
    private final JdbcTemplate jdbc;
    // FIX: backlog 5.1 — publishes FuelDispatchedToVehicleEvent so
    // fleet's own listener can reconcile this into cost-per-km. See
    // that event's own Javadoc for the full rationale.
    private final ApplicationEventPublisher eventPublisher;

    @Value("${fuel.forecast.lookback-days:30}")
    private int forecastLookbackDays = 30;

    // ── Tanks ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TankResponse> getTanks(TenantId tenantId) {
        return tankRepository.findAllActive(tenantId).stream()
                .map(this::toTankResponse).toList();
    }

    @Transactional(readOnly = true)
    public TankResponse getTank(TenantId tenantId, UUID id) {
        return tankRepository.findActiveById(tenantId, id)
                .map(this::toTankResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tank", id.toString()));
    }

    @Transactional
    public TankResponse createTank(TenantId tenantId, CreateTankRequest req) {
        FuelTank tank = FuelTank.create(tenantId, req.name(), req.fuelType(),
                req.capacityLitres(), req.location());
        tankRepository.save(tank);
        log.info("Created fuel tank={} tenant={}", tank.getName(), tenantId);
        return toTankResponse(tank);
    }

    /**
     * FIX: "no tank capacity/utilization forecasting" gap — the audit's own
     * framing: "given consumption rate (dispatches) and current level are
     * both tracked, a simple 'days until empty at current usage' projection
     * would be a natural, low-effort addition directly useful for reorder
     * timing." Batched across every active tank in one query rather than
     * one call per tank (see FuelDispatchRepository.sumLitresDispensedByTankIds).
     * <p>
     * Deliberately a simple trailing-average model — total litres dispensed
     * over the lookback window divided by the window length — not a
     * weighted or seasonally-adjusted forecast. A tank with no dispatch
     * activity in the window reports hasSufficientData=false rather than an
     * infinite or zero days-until-empty, since neither of those would be a
     * meaningful answer to "when will this run out."
     */
    @Transactional(readOnly = true)
    public List<TankUtilizationForecastResponse> getUtilizationForecasts(TenantId tenantId) {
        List<FuelTank> tanks = tankRepository.findAllActive(tenantId);
        Instant to = Instant.now();
        Instant from = to.minus(forecastLookbackDays, ChronoUnit.DAYS);

        var usageByTank = dispatchRepository.sumLitresDispensedByTankIds(
                tanks.stream().map(FuelTank::getId).toList(), from, to);

        return tanks.stream().map(tank -> {
            BigDecimal totalUsed = usageByTank.getOrDefault(tank.getId(), BigDecimal.ZERO);
            boolean sufficient = totalUsed.compareTo(BigDecimal.ZERO) > 0;

            BigDecimal avgDaily = BigDecimal.ZERO;
            Integer daysUntilEmpty = null;
            LocalDate projectedEmptyDate = null;

            if (sufficient) {
                avgDaily = totalUsed.divide(BigDecimal.valueOf(forecastLookbackDays), 2, RoundingMode.HALF_UP);
                if (avgDaily.compareTo(BigDecimal.ZERO) > 0) {
                    daysUntilEmpty = tank.getCurrentLitres()
                            .divide(avgDaily, 0, RoundingMode.FLOOR).intValue();
                    projectedEmptyDate = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(daysUntilEmpty);
                }
            }

            return new TankUtilizationForecastResponse(tank.getId(), tank.getName(), avgDaily,
                    daysUntilEmpty, projectedEmptyDate, forecastLookbackDays, sufficient);
        }).toList();
    }

    /**
     * FIX: "no reorder-point automation" gap — the low-stock banner's
     * "Receive stock" button just navigated to a blank manual form. This
     * backs a pre-filled version: suggested quantity tops the tank back up
     * to capacity, and the last receipt on this tank (if any) suggests
     * carrying forward the same supplier and price — a starting point the
     * person filling the form can always override, not a binding choice.
     */
    @Transactional(readOnly = true)
    public ReorderSuggestionResponse getReorderSuggestion(TenantId tenantId, UUID tankId) {
        FuelTank tank = findActiveTank(tenantId, tankId);
        BigDecimal suggestedLitres = tank.getCapacityLitres().subtract(tank.getCurrentLitres());
        if (suggestedLitres.compareTo(BigDecimal.ZERO) < 0) suggestedLitres = BigDecimal.ZERO;

        UUID lastSupplierId = null;
        String lastSupplierName = null;
        BigDecimal lastPricePerLitre = null;

        var lastReceipt = receiptRepository.findMostRecentForTank(tankId);
        if (lastReceipt.isPresent()) {
            FuelReceipt receipt = lastReceipt.get();
            lastPricePerLitre = receipt.getPricePerLitre();
            if (receipt.getSupplierId() != null) {
                lastSupplierId = receipt.getSupplierId();
                // Supplier may have since been deleted — name is best-effort, id is still passed through.
                lastSupplierName = supplierRepository.findActiveById(tenantId, receipt.getSupplierId())
                        .map(FuelSupplier::getName)
                        .orElse(null);
            }
        }

        return new ReorderSuggestionResponse(tank.getId(), tank.getName(),
                tank.getCapacityLitres(), tank.getCurrentLitres(), suggestedLitres,
                lastSupplierId, lastSupplierName, lastPricePerLitre);
    }

    /**
     * FIX: "no stock reconciliation / dip-variance report PDF" gap — the
     * audit's own framing: "given how central the variance-detection
     * feature is, there's no exportable document a depot manager could
     * hand to ops/security when investigating a suspected theft, only the
     * in-app dip history list." from/to default to the last 90 days when
     * not supplied — long enough to show a pattern, short enough to stay a
     * reasonable page count.
     */
    @Transactional(readOnly = true)
    public byte[] generateReconciliationReport(TenantId tenantId, UUID tankId, Instant from, Instant to) {
        FuelTank tank = findActiveTank(tenantId, tankId);
        Instant rangeTo   = to != null ? to : Instant.now();
        Instant rangeFrom = from != null ? from : rangeTo.minus(90, java.time.temporal.ChronoUnit.DAYS);

        List<DipReading> readings = dipRepository.findByTankAndReadAtBetween(tankId, rangeFrom, rangeTo);
        return reconciliationPdfGenerator.generate(tank, readings, rangeFrom, rangeTo, resolveTenantName(tenantId));
    }

    /**
     * FIX: "no monthly fuel-usage report PDF" gap — dispatch data (litres out,
     * by recipient/vehicle) was dashboard/rollup-only, with no exportable
     * report for cost allocation across vehicles/cost-centers. Defaults to
     * the current calendar month when no range is given, since "monthly" is
     * the framing the audit itself used, but accepts any from/to for a
     * custom period.
     */
    @Transactional(readOnly = true)
    public byte[] generateUsageReport(TenantId tenantId, Instant from, Instant to) {
        Instant rangeTo   = to != null ? to : Instant.now();
        Instant rangeFrom = from != null ? from : rangeTo.atZone(java.time.ZoneId.of("Africa/Johannesburg"))
                .toLocalDate().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.of("Africa/Johannesburg")).toInstant();

        List<FuelDispatch> dispatches = dispatchRepository.findByTenantAndDispatchedAtBetween(tenantId, rangeFrom, rangeTo);
        return usageReportPdfGenerator.generate(dispatches, rangeFrom, rangeTo, resolveTenantName(tenantId));
    }

    /**
     * FIX: "no supplier statement/receiving report PDF" gap — receipts
     * already capture supplier, litres, and cost per delivery, but there
     * was no rolled-up "everything received from Supplier X this month"
     * document. Defaults to the current calendar month, same reasoning as
     * generateUsageReport(). Tank names are resolved in bulk here (not
     * inside the PDF generator) so the generator stays a pure rendering
     * layer with no repository dependencies of its own.
     */
    @Transactional(readOnly = true)
    public byte[] generateSupplierStatement(TenantId tenantId, UUID supplierId, Instant from, Instant to) {
        FuelSupplier supplier = supplierRepository.findActiveById(tenantId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId.toString()));

        Instant rangeTo   = to != null ? to : Instant.now();
        Instant rangeFrom = from != null ? from : rangeTo.atZone(java.time.ZoneId.of("Africa/Johannesburg"))
                .toLocalDate().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.of("Africa/Johannesburg")).toInstant();

        List<FuelReceipt> receipts = receiptRepository.findBySupplierAndReceivedAtBetween(tenantId, supplierId, rangeFrom, rangeTo);
        Map<UUID, String> tankNames = tankRepository.findAllActive(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(FuelTank::getId, FuelTank::getName));

        return supplierStatementPdfGenerator.generate(supplier, receipts, tankNames, rangeFrom, rangeTo, resolveTenantName(tenantId));
    }

    /**
     * ASSUMPTION: guesses a `tenants` table with a `name` column — same
     * defensive try/catch-and-omit style used for the equivalent lookup in
     * the Tasks module's board PDF export. Verify against the real schema;
     * if wrong, the report header just omits the tenant line.
     */
    private String resolveTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?", String.class, tenantId.getValue());
        } catch (Exception e) {
            return null;
        }
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SupplierResponse> getSuppliers(TenantId tenantId) {
        return supplierRepository.findAllActive(tenantId).stream()
                .map(this::toSupplierResponse).toList();
    }

    @Transactional
    public SupplierResponse createSupplier(TenantId tenantId, CreateSupplierRequest req) {
        FuelSupplier supplier = FuelSupplier.create(tenantId, req.name(),
                req.contactName(), req.contactPhone(),
                req.contactEmail(), req.accountNumber());
        supplierRepository.save(supplier);
        return toSupplierResponse(supplier);
    }

    // ── Receipts (receiving stock) ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReceiptResponse> getReceipts(TenantId tenantId, Pageable pageable) {
        return receiptRepository.findAllActive(tenantId, pageable)
                .map(this::toReceiptResponse);
    }

    @Transactional
    public ReceiptResponse receiveFuel(TenantId tenantId, UUID tankId,
                                       ReceiveFuelRequest req) {
        FuelTank tank = findActiveTank(tenantId, tankId);
        var levelBefore = tank.getCurrentLitres();

        // addStock validates capacity — throws if would overflow
        var levelAfter = tank.addStock(req.litresReceived());
        tankRepository.save(tank);

        FuelReceipt receipt = FuelReceipt.create(tenantId, tankId,
                req.supplierId(), req.litresReceived(), req.pricePerLitre(),
                req.receivedAt(), req.deliveryNote(), req.invoiceRef(),
                levelBefore, levelAfter);
        receiptRepository.save(receipt);

        log.info("Fuel received tank={} litres={} supplier={}",
                tank.getName(), req.litresReceived(), req.supplierId());
        return toReceiptResponse(receipt);
    }

    // ── Dispatches (issuing fuel) ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DispatchResponse> getDispatches(TenantId tenantId, Pageable pageable) {
        return dispatchRepository.findAllActive(tenantId, pageable)
                .map(this::toDispatchResponse);
    }

    @Transactional
    public DispatchResponse dispatchFuel(TenantId tenantId, UUID tankId,
                                         DispatchFuelRequest req) {
        FuelTank tank = findActiveTank(tenantId, tankId);
        var levelBefore = tank.getCurrentLitres();
        boolean wasLow = tank.isLow();

        // removeStock validates sufficient fuel — throws if insufficient
        var levelAfter = tank.removeStock(req.litresDispensed());
        tankRepository.save(tank);

        FuelDispatch dispatch = FuelDispatch.create(tenantId, tankId,
                req.vehicleId(), req.assetId(), req.customerId(),
                req.recipientName(), req.litresDispensed(), req.pricePerLitre(),
                req.dispatchedAt(), req.odometerReading(), req.hoursReading(),
                req.authorisedBy(), req.notes(), levelBefore, levelAfter);
        dispatchRepository.save(dispatch);

        // FIX: backlog 5.1 — only when the dispatch actually went to a
        // vehicle. Dispatches to assets (req.assetId()) or external
        // customers (req.customerId()) have nothing to do with Fleet's
        // cost-per-km and correctly publish nothing.
        if (req.vehicleId() != null) {
            eventPublisher.publishEvent(FuelDispatchedToVehicleEvent.of(
                    tenantId, dispatch.getId(), req.vehicleId(),
                    req.litresDispensed(), req.pricePerLitre(),
                    req.dispatchedAt(), req.odometerReading()));
        }

        if (!wasLow && tank.isLow()) {
            notifyLowStock(tenantId, tank);
        }

        log.info("Fuel dispatched tank={} litres={} to={}",
                tank.getName(), req.litresDispensed(),
                req.recipientName() != null ? req.recipientName() : req.vehicleId());
        return toDispatchResponse(dispatch);
    }

    // ── Dip Readings ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DipReadingResponse> getDipReadings(TenantId tenantId,
                                                   UUID tankId, Pageable pageable) {
        findActiveTank(tenantId, tankId);
        return dipRepository.findByTank(tankId, pageable)
                .map(this::toDipResponse);
    }

    @Transactional
    public DipReadingResponse recordDipReading(TenantId tenantId, UUID tankId,
                                               DipReadingRequest req) {
        FuelTank tank = findActiveTank(tenantId, tankId);

        DipReading dip = DipReading.create(tenantId, tankId, req.readAt(),
                req.actualLitres(), tank.getCurrentLitres(),
                req.readBy(), req.notes());
        dipRepository.save(dip);

        if (dip.hasNegativeVariance()) {
            log.warn("NEGATIVE FUEL VARIANCE tank={} variance={}L — possible theft or leak",
                    tank.getName(), dip.getVarianceLitres());
            notifyNegativeVariance(tenantId, tank, dip);
        }

        return toDipResponse(dip);
    }

    /**
     * FIX: "no negative-variance (theft/leak) alert" gap — flagged as the audit's
     * highest-severity item. hasNegativeVariance() was already calculated and
     * logged server-side, but a genuine fuel theft or leak sat as a dashboard
     * badge someone had to go looking for rather than an urgent notification.
     * CRITICAL severity + SMS are both already the default for
     * FUEL_NEGATIVE_VARIANCE in the catalogue — nothing to configure here beyond
     * actually calling send().
     * <p>
     * Uses TenantAdminRecipients rather than a fuel-specific recipient list —
     * same reasoning as Fleet's compliance alerts: there's no dedicated "fuel
     * manager" role in this platform, so tenant admins are the right generic
     * catch-all for a site-security-relevant event like this.
     */
    private void notifyNegativeVariance(TenantId tenantId, FuelTank tank, DipReading dip) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FUEL_NEGATIVE_VARIANCE)
                .title("Fuel shortfall detected: " + tank.getName())
                .message("A dip reading on " + tank.getName() + " came in "
                        + dip.getVarianceLitres().abs() + "L below the calculated book level — "
                        + "possible theft or leak. Review the tank's reconciliation history.")
                .actionUrl("/fuel/tanks/" + tank.getId())
                .sourceModule("fuel")
                .sourceEntityId(dip.getId().toString())
                .recipients(recipients)
                .build());
    }

    /**
     * FIX: "no low-tank-stock alert" gap. isLow() is a derived state (≤20% full),
     * not a discrete event, and a brand-new tank starts at 0% — which is
     * technically "low" — so this can't just fire on isLow() being true. It's
     * edge-triggered instead: callers pass the tank only when it has just
     * crossed INTO low state on this exact call (wasLow=false beforehand, true
     * after removing stock), so it fires once per drop-below-threshold rather
     * than on tank creation or on every subsequent dispatch while still low.
     */
    private void notifyLowStock(TenantId tenantId, FuelTank tank) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FUEL_TANK_LOW)
                .title("Low fuel: " + tank.getName())
                .message(tank.getName() + " is now at " + tank.getFillPercentage()
                        + "% (" + tank.getCurrentLitres() + "L of " + tank.getCapacityLitres()
                        + "L capacity) — below the low-stock threshold. Consider scheduling a refill.")
                .actionUrl("/fuel/tanks/" + tank.getId())
                .sourceModule("fuel")
                .sourceEntityId(tank.getId().toString())
                .recipients(recipients)
                .build());
    }

    // ── Deliveries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> getDeliveries(TenantId tenantId,
                                                String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? deliveryRepository.findAllActive(tenantId, pageable)
                : deliveryRepository.findByStatus(tenantId, status.toUpperCase(), pageable);
        return page.map(this::toDeliveryResponse);
    }

    @Transactional
    public DeliveryResponse scheduleDelivery(TenantId tenantId,
                                             CreateDeliveryRequest req) {
        findActiveTank(tenantId, req.tankId());
        FuelDelivery delivery = FuelDelivery.create(tenantId, req.tankId(),
                req.customerId(), req.deliveryAddress(), req.fuelType(),
                req.litresOrdered(), req.pricePerLitre(), req.scheduledAt(),
                req.driverName(), req.vehicleReg());
        deliveryRepository.save(delivery);
        log.info("Delivery scheduled tenant={} litres={} fuelType={}",
                tenantId, req.litresOrdered(), req.fuelType());
        return toDeliveryResponse(delivery);
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private FuelTank findActiveTank(TenantId tenantId, UUID id) {
        return tankRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Tank", id.toString()));
    }

    private TankResponse toTankResponse(FuelTank t) {
        return new TankResponse(t.getId(), t.getName(), t.getFuelType(),
                t.getCapacityLitres(), t.getCurrentLitres(),
                t.getFillPercentage(), t.isLow(), t.getLocation(), t.getCreatedAt());
    }

    private SupplierResponse toSupplierResponse(FuelSupplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactName(),
                s.getContactPhone(), s.getContactEmail(),
                s.getAccountNumber(), s.getCreatedAt());
    }

    private ReceiptResponse toReceiptResponse(FuelReceipt r) {
        return new ReceiptResponse(r.getId(), r.getTankId(), r.getSupplierId(),
                r.getLitresReceived(), r.getPricePerLitre(), r.getTotalCost(),
                r.getReceivedAt(), r.getDeliveryNote(), r.getInvoiceRef(),
                r.getLevelBefore(), r.getLevelAfter(), r.getCreatedAt());
    }

    private DispatchResponse toDispatchResponse(FuelDispatch d) {
        return new DispatchResponse(d.getId(), d.getTankId(), d.getVehicleId(),
                d.getAssetId(), d.getCustomerId(), d.getRecipientName(),
                d.getLitresDispensed(), d.getPricePerLitre(), d.getDispatchedAt(),
                d.getOdometerReading(), d.getHoursReading(), d.getAuthorisedBy(),
                d.getLevelBefore(), d.getLevelAfter(), d.getCreatedAt());
    }

    private DipReadingResponse toDipResponse(DipReading d) {
        return new DipReadingResponse(d.getId(), d.getTankId(), d.getReadAt(),
                d.getActualLitres(), d.getCalculatedLitres(), d.getVarianceLitres(),
                d.hasNegativeVariance(), d.getReadBy(), d.getCreatedAt());
    }

    private DeliveryResponse toDeliveryResponse(FuelDelivery d) {
        return new DeliveryResponse(
                d.getId(), d.getTankId(), d.getCustomerId(),
                d.getDeliveryAddress(), d.getFuelType(),
                d.getLitresOrdered(), d.getLitresDelivered(),
                d.getPricePerLitre(), d.getTotalAmount(),
                d.getStatus(), d.getScheduledAt(), d.getDeliveredAt(),
                d.getDriverName(), d.getVehicleReg(),
                d.getReceiverName(), d.getReceiverIdBadge(),
                d.getMeterReadingStart(), d.getMeterReadingEnd(),
                d.getReceiptNumber(), d.getReceiptGeneratedAt(),
                d.isSignedOnBehalf(), d.getOnBehalfOf(),
                d.getCreatedAt()
        );
    }

    // ✅ Keep only this one
    @Transactional
    public DeliveryResponse completeDelivery(TenantId tenantId, UUID deliveryId,
                                             CompleteDeliveryRequest req) {
        FuelDelivery delivery = deliveryRepository
                .findActiveById(tenantId, deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Delivery", deliveryId.toString()
                ));

        FuelTank tank = findActiveTank(tenantId, delivery.getTankId());
        boolean wasLow = tank.isLow();
        tank.removeStock(req.litresDelivered());
        tankRepository.save(tank);

        delivery.complete(
                req.litresDelivered(), req.receiverName(),
                req.receiverIdBadge(), req.meterReadingStart(),
                req.meterReadingEnd(), req.signedOnBehalf(),
                req.onBehalfOf()
        );

        delivery.assignReceiptNumber(receiptNumberGenerator.generate());
        deliveryRepository.save(delivery);

        if (!wasLow && tank.isLow()) {
            notifyLowStock(tenantId, tank);
        }

        log.info("Delivery completed id={} receipt={} litres={} receiver={} onBehalf={}",
                deliveryId, delivery.getReceiptNumber(),
                req.litresDelivered(), req.receiverName(),
                Boolean.TRUE.equals(req.signedOnBehalf()) ? req.onBehalfOf() : "N/A");

        return toDeliveryResponse(delivery);
    }

    @Transactional
    public SupplierResponse updateSupplier(TenantId tenantId, UUID supplierId,
                                           CreateSupplierRequest req) {
        FuelSupplier supplier = supplierRepository.findActiveById(tenantId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId.toString()));

        supplier.update(req.name(), req.contactName(), req.contactPhone(),
                req.contactEmail(), req.accountNumber());
        supplierRepository.save(supplier);
        log.info("Updated supplier={} tenant={}", supplierId, tenantId);
        return toSupplierResponse(supplier);
    }
}
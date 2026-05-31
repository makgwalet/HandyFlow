// fuel/application/internal/FuelService.java

package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
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

        // removeStock validates sufficient fuel — throws if insufficient
        var levelAfter = tank.removeStock(req.litresDispensed());
        tankRepository.save(tank);

        FuelDispatch dispatch = FuelDispatch.create(tenantId, tankId,
                req.vehicleId(), req.assetId(), req.customerId(),
                req.recipientName(), req.litresDispensed(), req.pricePerLitre(),
                req.dispatchedAt(), req.odometerReading(), req.hoursReading(),
                req.authorisedBy(), req.notes(), levelBefore, levelAfter);
        dispatchRepository.save(dispatch);

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
        }

        return toDipResponse(dip);
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

        log.info("Delivery completed id={} receipt={} litres={} receiver={} onBehalf={}",
                deliveryId, delivery.getReceiptNumber(),
                req.litresDelivered(), req.receiverName(),
                Boolean.TRUE.equals(req.signedOnBehalf()) ? req.onBehalfOf() : "N/A");

        return toDeliveryResponse(delivery);
    }
}
package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryException;
import za.co.handyflow.platform.fuel.domain.model.FuelLoadReturn;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryExceptionRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelLoadReturnRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.Outstanding;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.ReturnRequest;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.ReturnView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 7a: confirms which tank fuel brought back on a truck was put into. It books no stock movement. A tank is only
 * deducted when a delivery completes, so fuel that came back was never taken out of the book balance; booking it "back"
 * would overstate stock. This only records where it physically went, and shows what was declared returned but not yet
 * confirmed into a tank.
 *
 * The most that can be confirmed for a load is what was declared returned on its failed attempts, plus, once it is
 * delivered, the loaded litres that were not delivered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelLoadReturnService {

    private final FuelDeliveryRepository deliveries;
    private final FuelDeliveryExceptionRepository exceptions;
    private final FuelLoadReturnRepository returns;
    private final FuelTankRepository tanks;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public List<ReturnView> list(TenantId tenantId, UUID deliveryId) {
        find(tenantId, deliveryId);
        Map<UUID, String> names = tankNames(tenantId);
        return returns.findByDelivery(tenantId, deliveryId).stream().map(r -> view(r, names)).toList();
    }

    /** Loads with declared returned litres not yet all confirmed into a tank, oldest first. */
    @Transactional(readOnly = true)
    public List<Outstanding> outstanding(TenantId tenantId) {
        Map<UUID, List<FuelDeliveryException>> byLoad = exceptions.findAllWithReturnedLitres(tenantId).stream()
                .collect(Collectors.groupingBy(FuelDeliveryException::getDeliveryId, LinkedHashMap::new, Collectors.toList()));
        if (byLoad.isEmpty()) return List.of();
        Map<UUID, BigDecimal> confirmed = confirmedByLoad(tenantId, byLoad.keySet());
        Map<UUID, String> customerNames = new HashMap<>();
        List<Outstanding> out = new ArrayList<>();
        for (var e : byLoad.entrySet()) {
            FuelDelivery d = deliveries.findActiveById(tenantId, e.getKey()).orElse(null);
            if (d == null) continue;
            BigDecimal declared = e.getValue().stream().map(FuelDeliveryException::getLitresReturned).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal back = confirmed.getOrDefault(d.getId(), BigDecimal.ZERO);
            BigDecimal left = declared.subtract(back);
            if (left.signum() <= 0) continue;
            String customer = d.getCustomerId() == null ? null
                    : customerNames.computeIfAbsent(d.getCustomerId(), id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse("(customer removed)"));
            out.add(new Outstanding(d.getId(), customer, d.getVehicleReg(), d.getDriverName(), d.getLoadedLitres(), declared, back, left,
                    e.getValue().get(e.getValue().size() - 1).getRecordedAt()));
        }
        out.sort(Comparator.comparing(Outstanding::lastAttemptAt));
        return out;
    }

    @Transactional
    public ReturnView record(TenantId tenantId, UUID deliveryId, ReturnRequest req, UUID by) {
        FuelDelivery d = deliveries.findActiveByIdForUpdate(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        // A retry of the same request returns the first record and changes nothing.
        if (req.clientRequestId() != null) {
            var existing = returns.findByClientRequest(tenantId, deliveryId, req.clientRequestId());
            if (existing.isPresent()) return view(existing.get(), tankNames(tenantId));
        }
        FuelTank tank = tanks.findActiveById(tenantId, req.tankId()).orElseThrow(() -> new ResourceNotFoundException("Tank", String.valueOf(req.tankId())));
        BigDecimal declared = exceptions.findByDelivery(tenantId, deliveryId).stream().map(FuelDeliveryException::getLitresReturned).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = "DELIVERED".equals(d.getStatus()) && d.getLoadedLitres() != null && d.getLitresDelivered() != null
                ? d.getLoadedLitres().subtract(d.getLitresDelivered()).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal ceiling = declared.add(remainder);
        BigDecimal already = returns.findByDelivery(tenantId, deliveryId).stream().map(FuelLoadReturn::getLitres).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ceiling.signum() <= 0)
            throw new IllegalArgumentException("No litres were declared returned for this load, and it has no loaded litres left over. Record the failed attempt with the litres brought back first.");
        BigDecimal room = ceiling.subtract(already);
        if (req.litres() == null || req.litres().compareTo(room) > 0)
            throw new IllegalArgumentException("Only " + room.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " L can still be confirmed back for this load.");
        FuelLoadReturn saved = returns.save(FuelLoadReturn.create(tenantId, deliveryId, tank.getId(), req.litres(), req.note(), by, req.clientRequestId()));
        log.info("Load {} returned {} L into tank {}", deliveryId, req.litres(), tank.getId());
        return view(saved, Map.of(tank.getId(), tank.getName()));
    }

    private Map<UUID, BigDecimal> confirmedByLoad(TenantId tenantId, Collection<UUID> ids) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (FuelLoadReturn r : returns.findByDeliveries(tenantId, ids)) m.merge(r.getDeliveryId(), r.getLitres(), BigDecimal::add);
        return m;
    }

    private Map<UUID, String> tankNames(TenantId tenantId) {
        Map<UUID, String> m = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) m.put(t.getId(), t.getName());
        return m;
    }

    private FuelDelivery find(TenantId tenantId, UUID id) {
        return deliveries.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Delivery", id.toString()));
    }

    private static ReturnView view(FuelLoadReturn r, Map<UUID, String> names) {
        return new ReturnView(r.getId(), r.getDeliveryId(), r.getTankId(), names.get(r.getTankId()), r.getLitres(), r.getNote(), r.getRecordedBy(), r.getRecordedAt());
    }
}

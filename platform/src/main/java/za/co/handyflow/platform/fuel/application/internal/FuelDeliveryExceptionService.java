package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryException;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryExceptionRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.ExceptionView;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.FailedAttemptRequest;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.WindowRequest;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * C04: delivery windows and failed attempts. A failed attempt puts the load back to Scheduled for another try and keeps an
 * append-only record with the reason. It never touches stock, invoicing or variance: stock is only deducted when a delivery
 * is completed, so litres brought back to the depot are recorded here as information, not booked back into a tank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelDeliveryExceptionService {

    private final FuelDeliveryRepository deliveries;
    private final FuelDeliveryExceptionRepository exceptions;
    private final HrFacade hr;

    @Transactional(readOnly = true)
    public List<ExceptionView> list(TenantId tenantId, UUID deliveryId) {
        find(tenantId, deliveryId);
        return exceptions.findByDelivery(tenantId, deliveryId).stream().map(FuelDeliveryExceptionService::view).toList();
    }

    @Transactional
    public void setWindow(TenantId tenantId, UUID deliveryId, WindowRequest req) {
        FuelDelivery d = findForUpdate(tenantId, deliveryId);
        d.setWindow(req.windowStart(), req.windowEnd());
        deliveries.save(d);
    }

    /** Office staff may record a failed attempt on any load that is on the road. */
    @Transactional
    public ExceptionView recordAsOffice(TenantId tenantId, UUID deliveryId, FailedAttemptRequest req, UUID by) {
        return record(tenantId, findForUpdate(tenantId, deliveryId), req, by);
    }

    /** A driver may record one only on their own load. Another driver's load looks like a missing one. */
    @Transactional
    public ExceptionView recordAsDriver(TenantId tenantId, UUID userId, UUID deliveryId, FailedAttemptRequest req) {
        if (userId == null) throw new IllegalStateException("You are not signed in as a user.");
        EmployeeSummary me = hr.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
        FuelDelivery d = findForUpdate(tenantId, deliveryId);
        if (!me.id().equals(d.getDriverEmployeeId())) throw new ResourceNotFoundException("Delivery", deliveryId.toString());
        return record(tenantId, d, req, userId);
    }

    private ExceptionView record(TenantId tenantId, FuelDelivery d, FailedAttemptRequest req, UUID by) {
        // A retry of the same request (same client id) returns the first record and changes nothing.
        if (req.clientRequestId() != null) {
            var existing = exceptions.findByClientRequest(tenantId, d.getId(), req.clientRequestId());
            if (existing.isPresent()) return view(existing.get());
        }
        BigDecimal returned = req.litresReturned() == null ? BigDecimal.ZERO : req.litresReturned();
        if (returned.signum() < 0) throw new IllegalArgumentException("Litres returned cannot be negative.");
        BigDecimal loaded = d.getLoadedLitres();
        if (returned.signum() > 0 && (loaded == null || returned.compareTo(loaded) > 0))
            throw new IllegalArgumentException(loaded == null ? "No loading was recorded for this load, so litres cannot be returned."
                    : "Litres returned cannot be more than the " + loaded.stripTrailingZeros().toPlainString() + " L loaded.");
        // Validate the reason before the load changes state.
        FuelDeliveryException probe = FuelDeliveryException.create(tenantId, d.getId(), req.reasonCode(), req.note(), returned, req.rescheduleTo(), d.getFailedAttempts() + 1, by, req.clientRequestId());
        int attempt = d.recordFailedAttempt(req.rescheduleTo());
        deliveries.save(d);
        log.info("Delivery {} failed attempt {} ({})", d.getId(), attempt, req.reasonCode());
        return view(exceptions.save(probe));
    }

    private FuelDelivery find(TenantId tenantId, UUID id) {
        return deliveries.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Delivery", id.toString()));
    }

    private FuelDelivery findForUpdate(TenantId tenantId, UUID id) {
        return deliveries.findActiveByIdForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Delivery", id.toString()));
    }

    private static ExceptionView view(FuelDeliveryException e) {
        return new ExceptionView(e.getId(), e.getDeliveryId(), e.getKind(), e.getReasonCode(), e.getNote(), e.getLitresReturned(),
                e.getRescheduledTo(), e.getAttemptNumber(), e.getRecordedBy(), e.getRecordedAt());
    }
}

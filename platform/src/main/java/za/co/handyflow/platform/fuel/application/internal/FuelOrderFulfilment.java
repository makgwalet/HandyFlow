package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.fuel.domain.model.FuelReservation;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReservationRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSalesOrderRepository;
import za.co.handyflow.platform.fuel.dto.ReservationRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Keeps an order in step with its deliveries: status follows the litres actually delivered, and the order's
 * stock reservation is resized to what is still owed. Called inside the caller's transaction after a delivery
 * completes or is reversed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelOrderFulfilment {

    private final FuelSalesOrderRepository orders;
    private final FuelDeliveryRepository deliveries;
    private final FuelReservationRepository reservations;
    private final FuelStockService stock;

    public void resync(TenantId tenantId, UUID orderId) {
        FuelSalesOrder order = orders.findActiveByIdForUpdate(tenantId, orderId).orElse(null);
        if (order == null) return;
        BigDecimal delivered = deliveries.sumDeliveredForOrder(tenantId, orderId);
        order.followDelivered(delivered);
        orders.save(order);

        // Whatever is still held for this order is replaced by exactly what is still owed.
        if (order.getTankId() != null) {
            for (FuelReservation r : reservations.findActiveByReference(tenantId, order.getTankId(), "ORDER", orderId)) {
                r.release();
                reservations.save(r);
            }
            BigDecimal remaining = order.getLitresOrdered().subtract(delivered);
            if (order.isOpen() && remaining.signum() > 0) {
                try {
                    stock.reserve(tenantId, new ReservationRequest(order.getTankId(), remaining, order.getCrmCustomerId(),
                            "ORDER", orderId, null, "Order " + order.getOrderNumber()), null);
                } catch (IllegalStateException e) {
                    // e.g. a delivery was reversed but the stock was meanwhile promised elsewhere. Never block the correction.
                    log.warn("Order {} could not re-reserve {} L: {}", order.getOrderNumber(), remaining, e.getMessage());
                }
            }
        }
    }
}

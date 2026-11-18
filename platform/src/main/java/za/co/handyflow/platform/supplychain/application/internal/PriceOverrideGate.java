package za.co.handyflow.platform.supplychain.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Keeps a purchase order from being approved while a buyer's price increase on it is still waiting for a second person. */
@Component
public class PriceOverrideGate {

    private final JdbcTemplate jdbc;

    public PriceOverrideGate(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireNonePending(TenantId tenantId, UUID purchaseOrderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM sc_requisition_conversions WHERE tenant_id = ? "
                + "AND purchase_order_id = ? AND price_status = 'PENDING_APPROVAL'", Integer.class, tenantId.getValue(), purchaseOrderId);
        if (n != null && n > 0)
            throw new HandyFlowException("This purchase order has " + n + " price increase(s) beyond tolerance that still need approval "
                    + "(SCM_PRICE_OVERRIDE_APPROVE) before the order can be approved.", HttpStatus.CONFLICT, "PRICE_OVERRIDE_PENDING");
    }
}

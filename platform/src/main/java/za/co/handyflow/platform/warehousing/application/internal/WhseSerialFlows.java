package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Receiving and returning with serial numbers. The stock movement and the serials are written in one transaction, so a receipt whose
 * serials are wrong (a duplicate, the wrong count) leaves no stock behind.
 */
@Service
@RequiredArgsConstructor
public class WhseSerialFlows {

    private final WhseReceivingService receiving;
    private final WhseReturnsService returns;
    private final WhseSerialService serials;

    @Transactional
    public UUID receive(TenantId tenantId, UUID shipmentId, UUID lineId, BigDecimal good, BigDecimal damaged, BigDecimal held, UUID locationId, String reason,
                        String lotNumber, LocalDate expiryDate, LocalDate manufacturedDate, String idempotencyKey, List<String> serialNumbers, UUID actor) {
        UUID receiptId = receiving.receive(tenantId, shipmentId, lineId, good, damaged, held, locationId, reason, lotNumber, expiryDate, manufacturedDate, idempotencyKey, actor);
        serials.registerReceipt(tenantId, shipmentId, lineId, receiptId, nz(good).add(nz(damaged)).add(nz(held)), serialNumbers, actor);
        return receiptId;
    }

    @Transactional
    public UUID receiveReturn(TenantId tenantId, UUID returnId, UUID lineId, BigDecimal restock, BigDecimal held, BigDecimal damaged, UUID locationId, String reason,
                              String lotNumber, String idempotencyKey, List<String> serialNumbers, UUID actor) {
        UUID receiptId = returns.receive(tenantId, returnId, lineId, restock, held, damaged, locationId, reason, lotNumber, idempotencyKey, actor);
        serials.acceptReturn(tenantId, returnId, lineId, nz(restock).add(nz(held)).add(nz(damaged)), serialNumbers, actor);
        return receiptId;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}

package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Quality inspection of received goods. */
public final class QualityDtos {

    private QualityDtos() {}

    public record StartRequest(UUID goodsReceiptId) {}

    public record LineResult(UUID lineId, BigDecimal qtyPassed, BigDecimal qtyFailed, String defectCode, String notes) {}

    public record RecordRequest(List<LineResult> lines, String notes) {}

    public record CompleteRequest(boolean raiseReturnForFailures) {}

    public record LineResponse(UUID id, UUID grLineId, String itemName, BigDecimal qtyAcceptedOnReceipt, BigDecimal qtyInspected,
                               BigDecimal qtyPassed, BigDecimal qtyFailed, String defectCode, String notes) {}

    public record Response(UUID id, String inspectionNumber, UUID goodsReceiptId, UUID supplierId, String status, String result, String notes,
                           String inspectedByName, Instant completedAt, UUID returnId, Instant createdAt, List<LineResponse> lines) {}
}

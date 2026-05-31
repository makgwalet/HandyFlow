package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record ReceiveStockRequest(List<ReceivedLine> lines) {
    public record ReceivedLine(UUID itemId, BigDecimal qtyReceived) {}
}

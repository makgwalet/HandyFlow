package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record PurchaseOrderResponse(
        UUID       id, String orderNumber,
        UUID       supplierId, String supplierName,
        String     status, LocalDate orderDate,
        LocalDate  expectedDate, LocalDate receivedDate,
        BigDecimal subtotal, BigDecimal vatAmount, BigDecimal totalAmount,
        String     notes,
        List<PurchaseOrderItemResponse> items,
        Instant    createdAt
) {}

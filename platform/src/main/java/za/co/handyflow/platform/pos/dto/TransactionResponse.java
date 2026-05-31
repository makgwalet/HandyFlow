package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record TransactionResponse(
        UUID       id, String transactionNumber,
        UUID       customerId, String customerName,
        BigDecimal subtotal, BigDecimal vatAmount,
        BigDecimal discountAmount, BigDecimal totalAmount,
        String     paymentMethod, BigDecimal amountTendered, BigDecimal changeGiven,
        String     paymentRef, String status,
        String     servedByName,
        List<TransactionItemResponse> items,
        Instant    createdAt
) {}
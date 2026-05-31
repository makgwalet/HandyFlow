package za.co.handyflow.platform.property.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.time.LocalDate; import java.util.UUID;
public record PaymentResponse(
        UUID id, UUID leaseId, Integer periodYear, Integer periodMonth,
        BigDecimal amountDue, BigDecimal amountPaid, BigDecimal balance,
        LocalDate dueDate, LocalDate paidDate, String paymentMethod,
        String reference, String status, Instant createdAt
) {}
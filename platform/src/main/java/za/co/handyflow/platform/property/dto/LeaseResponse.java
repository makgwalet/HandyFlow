package za.co.handyflow.platform.property.dto;
import java.math.BigDecimal; import java.time.Instant;
import java.time.LocalDate; import java.util.UUID;
public record LeaseResponse(
        UUID id, UUID unitId, UUID customerId,
        String lesseeName, String lesseeEmail, String lesseePhone,
        LocalDate startDate, LocalDate endDate,
        BigDecimal monthlyRent, BigDecimal depositAmount,
        boolean depositPaid, Integer paymentDay,
        BigDecimal escalationRate, String status,
        boolean monthToMonth, boolean expiringSoon,
        Instant createdAt
) {}
package za.co.handyflow.platform.tasks.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record TimeLogResponse(
        UUID id, UUID userId, String userName,
        BigDecimal hours, String description,
        LocalDate loggedDate, Instant createdAt
) {}
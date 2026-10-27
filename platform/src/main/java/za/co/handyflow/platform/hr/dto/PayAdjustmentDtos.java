package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PayAdjustmentDtos {

    private PayAdjustmentDtos() {}

    public record AddRequest(String kind, BigDecimal amount, LocalDate payFrom, String description, LocalDate relatesFrom, LocalDate relatesTo, String reason) {}

    public record CancelRequest(String reason) {}

    /** {@code payRunId} is set once a pay run has taken the item; {@code canCancel} is false from then on. */
    public record View(UUID id, UUID employeeId, String employeeNumber, String employeeName, String kind, String kindLabel, BigDecimal amount,
                       LocalDate payFrom, String description, LocalDate relatesFrom, LocalDate relatesTo, String reason, String status,
                       UUID payRunId, String payRunNumber, boolean canCancel, String cancelReason, Instant createdAt) {}
}

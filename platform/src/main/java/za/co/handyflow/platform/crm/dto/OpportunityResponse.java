package za.co.handyflow.platform.crm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.LostReason;
import za.co.handyflow.platform.crm.domain.model.OpportunityStage;

public record OpportunityResponse(
        UUID id, UUID customerId, String customerName, String name, OpportunityStage stage, int chance,
        BigDecimal amount, BigDecimal weightedAmount, LocalDate expectedCloseDate, UUID ownerId,
        LostReason lostReason, String lostNote, String notes, Instant closedAt, Instant createdAt, Instant updatedAt
) {}

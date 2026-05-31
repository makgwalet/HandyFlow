package za.co.handyflow.platform.recruiter.dto;
import java.time.Instant;
public record StageHistoryResponse(
        String fromStage, String toStage,
        String changedByName, String notes, Instant createdAt
) {}
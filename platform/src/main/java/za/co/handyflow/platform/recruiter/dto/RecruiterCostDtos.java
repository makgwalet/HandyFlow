package za.co.handyflow.platform.recruiter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RecruiterCostDtos {
    private RecruiterCostDtos() {}

    public record CostRequest(String category, BigDecimal amount, LocalDate incurredOn, String note) {}
    public record Cost(UUID id, String category, BigDecimal amount, LocalDate incurredOn, String note) {}
    public record JobCosts(UUID jobId, BigDecimal total, List<Cost> costs) {}
}

package za.co.handyflow.platform.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TrainingCostDtos {
    private TrainingCostDtos() {}

    public record CostRequest(String category, BigDecimal amount, LocalDate incurredOn, String note) {}
    public record Cost(UUID id, String category, BigDecimal amount, LocalDate incurredOn, String note) {}
    /** {@code perPerson} is the total over the people who attended or completed; null until someone has. */
    public record SessionCosts(UUID sessionId, BigDecimal total, long people, BigDecimal perPerson, List<Cost> costs) {}
    public record PassMarkRequest(BigDecimal passMark) {}
}

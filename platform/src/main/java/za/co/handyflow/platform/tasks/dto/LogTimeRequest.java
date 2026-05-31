package za.co.handyflow.platform.tasks.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
public record LogTimeRequest(
        @NotNull @DecimalMin("0.1") BigDecimal hours,
        String    description,
        LocalDate loggedDate
) {}
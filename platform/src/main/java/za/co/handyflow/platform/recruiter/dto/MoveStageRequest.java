package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * offeredSalary/offeredSalaryFrequency/offeredStartDate/offerBenefits are
 * only meaningful when stage == "OFFER" — RecruiterService ignores them for
 * every other stage transition. Reusing this DTO (rather than a separate
 * "extend offer" endpoint) keeps offer-extension a single action from the
 * UI's existing move-stage flow, at the cost of a request shape that's
 * mostly-null outside the OFFER case.
 */
public record MoveStageRequest(
        @NotBlank String stage,    // SCREENING|INTERVIEW|ASSESSMENT|OFFER|HIRED|REJECTED
        String notes,
        String rejectionReason,
        BigDecimal offeredSalary,
        String     offeredSalaryFrequency,
        LocalDate  offeredStartDate,
        String     offerBenefits
) {}
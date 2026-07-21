package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * createHrRecord: false for external/agency placements (candidate joins a
 * client company, not this tenant) — no hr_employees row is created, the
 * application is just marked as placed. true for internal hires — routes
 * through the real HR module.
 *
 * grossSalary is required when createHrRecord = true (validated in
 * RecruiterService, not here — @NotNull can't be made conditional on a
 * sibling field in a record). It's asked for explicitly rather than derived
 * from the job's salaryMin/salaryMax, since those are a posted range, not
 * the actual agreed offer.
 *
 * employmentType / payFrequency are optional passthroughs — HrEmployee.create()
 * already defaults them (PERMANENT / MONTHLY) when null.
 */
public record ConvertToEmployeeRequest(
        @NotNull LocalDate startDate,
        String     jobTitle,
        String     department,
        boolean    createHrRecord,
        BigDecimal grossSalary,
        String     employmentType,
        String     payFrequency
) {}
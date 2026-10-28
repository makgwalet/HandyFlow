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
 * idNumber, taxNumber and the bank details are optional. When the new hire has given them they arrive with the employee, checked by
 * HR's own rules (a South African ID number, a 10-digit tax number, a bank name with an account and branch); when they have not, they stay
 * empty and the employee's onboarding keeps asking for them.
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
        String     payFrequency,
        String     idNumber,
        String     taxNumber,
        String     bankName,
        String     bankAccountNumber,
        String     bankBranchCode
) {
    /** The request as it was before the optional payroll details were added. */
    public ConvertToEmployeeRequest(LocalDate startDate, String jobTitle, String department, boolean createHrRecord,
                                    BigDecimal grossSalary, String employmentType, String payFrequency) {
        this(startDate, jobTitle, department, createHrRecord, grossSalary, employmentType, payFrequency, null, null, null, null, null);
    }
}
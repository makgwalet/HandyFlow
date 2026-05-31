package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CreateJobRequest(
        @NotBlank String title, String department, String location,
        String jobType, String experienceLevel,
        @NotBlank String description, String requirements, String benefits,
        BigDecimal salaryMin, BigDecimal salaryMax, boolean showSalary,
        LocalDate closesAt
) {}
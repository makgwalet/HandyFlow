package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
public record ConvertToEmployeeRequest(
        @NotNull LocalDate startDate,
        String    jobTitle,
        String    department
) {}
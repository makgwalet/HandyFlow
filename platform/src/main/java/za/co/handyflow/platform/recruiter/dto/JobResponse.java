package za.co.handyflow.platform.recruiter.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record JobResponse(
        UUID       id, String title, String department, String location,
        String     jobType, String experienceLevel,
        String     description, String requirements, String benefits,
        BigDecimal salaryMin, BigDecimal salaryMax, boolean showSalary,
        String     status, String slug, LocalDate closesAt,
        int        applicationCount, Instant createdAt
) {}

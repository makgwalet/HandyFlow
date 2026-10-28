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
        int        applicationCount,
        String     companyName,   // null on staff-facing responses (they know their own company); populated on public careers responses
        Instant createdAt,
        Integer retentionMonths,  // only on the public careers job: how long the company keeps an applicant's details, for the consent text
        String privacyNoticeUrl   // only on the public careers job: the company's privacy notice, linked from the consent text
) {
    /** Without the privacy-notice link, as before it was shown on the careers page. */
    public JobResponse(UUID id, String title, String department, String location, String jobType, String experienceLevel,
                       String description, String requirements, String benefits, BigDecimal salaryMin, BigDecimal salaryMax, boolean showSalary,
                       String status, String slug, LocalDate closesAt, int applicationCount, String companyName, Instant createdAt, Integer retentionMonths) {
        this(id, title, department, location, jobType, experienceLevel, description, requirements, benefits, salaryMin, salaryMax, showSalary,
                status, slug, closesAt, applicationCount, companyName, createdAt, retentionMonths, null);
    }

    /** Without the retention period, as before it was shown on the careers page. */
    public JobResponse(UUID id, String title, String department, String location, String jobType, String experienceLevel,
                       String description, String requirements, String benefits, BigDecimal salaryMin, BigDecimal salaryMax, boolean showSalary,
                       String status, String slug, LocalDate closesAt, int applicationCount, String companyName, Instant createdAt) {
        this(id, title, department, location, jobType, experienceLevel, description, requirements, benefits, salaryMin, salaryMax, showSalary,
                status, slug, closesAt, applicationCount, companyName, createdAt, null, null);
    }

    public JobResponse withRetentionMonths(int months) {
        return new JobResponse(id, title, department, location, jobType, experienceLevel, description, requirements, benefits, salaryMin, salaryMax,
                showSalary, status, slug, closesAt, applicationCount, companyName, createdAt, months, privacyNoticeUrl);
    }

    public JobResponse withPrivacyNoticeUrl(String url) {
        return new JobResponse(id, title, department, location, jobType, experienceLevel, description, requirements, benefits, salaryMin, salaryMax,
                showSalary, status, slug, closesAt, applicationCount, companyName, createdAt, retentionMonths, url);
    }
}

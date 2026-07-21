package za.co.handyflow.platform.recruiter.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record ApplicationResponse(
        UUID   id, UUID jobId, String jobTitle,
        UUID   applicantId, String applicantName, String applicantEmail,
        String applicantPhone, boolean hasCv,
        String stage, String source, Integer score,
        String notes, String rejectionReason,
        UUID   hrEmployeeId,
        List<InterviewResponse> interviews,
        List<StageHistoryResponse> history,
        Instant appliedAt, Instant stageChangedAt, Instant hiredAt,
        BigDecimal offeredSalary, String offeredSalaryFrequency,
        LocalDate  offeredStartDate, String offerBenefits, Instant offerLetterSentAt,
        String referrerName, UUID referredByUserId, String referredByUserName,
        BigDecimal referralBonusAmount, String referralBonusStatus, Instant referralBonusPaidAt
) {}
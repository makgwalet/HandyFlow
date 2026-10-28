package za.co.handyflow.platform.recruiter.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruiterRequisitionDtos {
    private RecruiterRequisitionDtos() {}

    public record SetRequisitionRequest(Integer vacancies, String requisitionRef) {}

    public record JobVersion(int versionNo, String title, String description, String requirements, String benefits,
                             BigDecimal salaryMin, BigDecimal salaryMax, UUID changedBy, Instant changedAt) {}

    public record RequisitionView(UUID jobId, int vacancies, String requisitionRef, int hired, int stillOpen, String status, List<JobVersion> versions) {}
}

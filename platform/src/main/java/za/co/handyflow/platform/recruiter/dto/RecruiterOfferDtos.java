package za.co.handyflow.platform.recruiter.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RecruiterOfferDtos {
    private RecruiterOfferDtos() {}

    /** The date the offer stays open until; empty means the default. */
    public record SendOfferRequest(LocalDate expiresOn) {}

    /** response: ACCEPTED, DECLINED or WITHDRAWN. A reason is required for the last two. */
    public record OfferResponseRequest(String response, String note) {}

    public record ReviseOfferRequest(BigDecimal offeredSalary, String offeredSalaryFrequency, LocalDate offeredStartDate,
                                     String offerBenefits, String reason) {}

    public record OfferRevision(BigDecimal oldSalary, String oldFrequency, LocalDate oldStartDate, String oldBenefits,
                                String reason, UUID revisedBy, Instant revisedAt) {}

    public record OfferView(UUID applicationId, String status, BigDecimal offeredSalary, String offeredSalaryFrequency,
                            LocalDate offeredStartDate, String offerBenefits, Instant sentAt, LocalDate expiresOn,
                            String response, Instant respondedAt, String responseNote, List<OfferRevision> revisions) {}
}

package za.co.handyflow.platform.recruiter.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruiterPoolDtos {
    private RecruiterPoolDtos() {}

    public record PoolRequest(String name, String description) {}
    public record PoolView(UUID id, String name, String description, int members) {}
    public record AddMemberRequest(UUID applicantId, String note) {}
    public record TagsRequest(List<String> tags) {}
    public record ApplyToJobRequest(UUID jobId) {}

    public record CandidateView(UUID applicantId, String name, String email, String phone, String location, List<String> tags,
                                int applications, Instant lastActivity, String note, Instant addedAt) {}
}

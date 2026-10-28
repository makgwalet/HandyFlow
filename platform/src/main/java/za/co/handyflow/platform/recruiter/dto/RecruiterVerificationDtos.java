package za.co.handyflow.platform.recruiter.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruiterVerificationDtos {
    private RecruiterVerificationDtos() {}

    public record AddCheckRequest(String type, String description, Boolean required) {}
    public record DecisionRequest(String status, String note) {}
    public record CheckView(UUID id, String type, String description, boolean required, String status, String note, Instant requestedAt, Instant decidedAt) {}
    public record VerificationView(UUID applicationId, List<CheckView> checks, int outstanding, boolean readyToHire, String blockedReason) {}
}

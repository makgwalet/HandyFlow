package za.co.handyflow.platform.recruitmentagency.dto;

import java.time.Instant;
import java.util.UUID;

public record CandidateResponse(
        UUID id, String fullName, String email, String phone,
        String currentTitle, String currentEmployer, String skills, String source,
        String cvFileName, boolean hasCv, String notes, String status, Instant createdAt
) {}
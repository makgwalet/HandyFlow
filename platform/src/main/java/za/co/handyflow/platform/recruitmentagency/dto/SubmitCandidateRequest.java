package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitCandidateRequest(@NotNull UUID candidateId) {}
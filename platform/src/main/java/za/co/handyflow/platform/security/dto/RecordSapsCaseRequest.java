package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordSapsCaseRequest(@NotBlank @Size(max = 60) String caseNumber) {}

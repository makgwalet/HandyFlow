package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotNull;

public record PayrollSettingsRequest(@NotNull(message = "Say whether one person may prepare and approve") Boolean allowSelfApproval) {}

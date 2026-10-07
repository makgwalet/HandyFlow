package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddTenderLookupValueRequest(@NotBlank @Size(max = 100) String value) {}

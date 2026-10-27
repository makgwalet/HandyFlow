package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.Size;

public record CompleteInspectionRequest(@Size(max = 2000) String notes) {}

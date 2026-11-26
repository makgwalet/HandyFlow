package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.Size;

public record DecideChangeRequest(@Size(max = 500) String note) {}

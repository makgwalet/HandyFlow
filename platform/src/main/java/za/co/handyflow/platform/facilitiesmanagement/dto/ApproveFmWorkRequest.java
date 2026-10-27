package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.LocalDate;

/** All fields optional. A priority override cannot go below the safety/emergency floor. */
public record ApproveFmWorkRequest(String priority, LocalDate scheduledDate, Boolean requiresVerification) {}
package za.co.handyflow.platform.facilities.dto;

import java.time.LocalDate;

/** All fields optional. A priority override cannot go below the safety/emergency floor. */
public record ApproveWorkRequest(String priority, LocalDate scheduledDate, Boolean requiresVerification) {}
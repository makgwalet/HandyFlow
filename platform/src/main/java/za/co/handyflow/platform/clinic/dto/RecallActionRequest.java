package za.co.handyflow.platform.clinic.dto;

import java.time.LocalDate;

/** type: CONTACT (outcome required), SNOOZE (snoozeUntil required), DISMISS (note = reason, required), REOPEN. */
public record RecallActionRequest(String type, String outcome, String note, LocalDate snoozeUntil) {}

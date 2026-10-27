package za.co.handyflow.platform.facilities.dto;

import java.time.LocalDate;

/** Change how often it runs and/or when it is next due. Either may be null to leave it as is. */
public record UpdateInspectionScheduleRequest(Integer frequencyDays, LocalDate nextDueDate) {}

package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;

/** expectedDueDate is optional — null leaves the record's existing estimate unchanged. */
public record ConfirmPregnantRequest(LocalDate expectedDueDate) {}

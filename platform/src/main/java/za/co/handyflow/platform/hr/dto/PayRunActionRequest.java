package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.Size;

/** The note that goes with approving, sending back or finalising a pay run. Required when sending back. */
public record PayRunActionRequest(@Size(max = 500, message = "The note can be at most 500 characters") String note) {}

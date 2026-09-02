package za.co.handyflow.platform.legalpractice.dto;

import java.time.LocalDate;

/** {@code closedDate} is optional — {@code LpMatter.close()} defaults to today when null. */
public record CloseMatterRequest(LocalDate closedDate) {}

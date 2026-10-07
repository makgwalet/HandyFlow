package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

/** One entry in a patient's timeline. {@code kind} is APPOINTMENT, CONSULTATION, PRESCRIPTION, LAB, CLAIM or PAYMENT. */
public record TimelineEvent(String kind, UUID id, Instant at, String title, String detail, String status) {}

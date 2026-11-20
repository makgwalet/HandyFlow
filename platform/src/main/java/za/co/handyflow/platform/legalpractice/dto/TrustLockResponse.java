package za.co.handyflow.platform.legalpractice.dto;

import java.time.LocalDate;

/** The trust ledger takes no transaction dated on or before {@code lockedThrough}; null when no period has been reconciled yet. */
public record TrustLockResponse(LocalDate lockedThrough) {}

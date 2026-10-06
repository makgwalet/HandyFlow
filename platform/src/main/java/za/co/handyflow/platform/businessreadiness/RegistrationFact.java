package za.co.handyflow.platform.businessreadiness;

import java.time.LocalDate;

/** What the evaluator needs to know about one registration, whichever business owns it. {@code status} is ACTIVE, EXPIRED, LAPSED, PENDING or NOT_APPLICABLE. */
public record RegistrationFact(String authority, String registrationType, String status, LocalDate expiryDate) {}

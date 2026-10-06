package za.co.handyflow.platform.businessreadiness;

import java.time.LocalDate;

/** What the evaluator needs to know about one compliance document, whichever business owns it. */
public record DocumentFact(String documentType, LocalDate expiryDate, boolean verified) {}

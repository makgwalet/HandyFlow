package za.co.handyflow.platform.facilities.application.internal;

import java.time.LocalDate;

/** How failures turn into corrective actions: priority and due date. Kept in one place so it is easy to change and test. */
public final class FacilityActionRules {

    public static final int CRITICAL_DUE_DAYS = 2;
    public static final int STANDARD_DUE_DAYS = 14;
    public static final int LAPSED_CERTIFICATE_DUE_DAYS = 7;

    private FacilityActionRules() {}

    /** A failed critical check is HIGH (safety/compliance); anything else is NORMAL. */
    public static String priorityForFailure(boolean critical) {
        return critical ? "HIGH" : "NORMAL";
    }

    public static LocalDate dueDateForFailure(boolean critical, LocalDate today) {
        return today.plusDays(critical ? CRITICAL_DUE_DAYS : STANDARD_DUE_DAYS);
    }

    /** A lapsed certificate is a legal/insurance exposure: always critical, a week to renew. */
    public static LocalDate dueDateForLapsedCertificate(LocalDate today) {
        return today.plusDays(LAPSED_CERTIFICATE_DUE_DAYS);
    }
}

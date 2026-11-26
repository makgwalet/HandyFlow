package za.co.handyflow.platform.payrollbureau.domain.rules;

/** When a payslip email is sent or retried (audit 2026-11-26, D10). */
public final class DeliveryRules {

    /** After this many failed attempts the automatic retry stops; a person can still re-send by hand. */
    public static final int MAX_AUTO_ATTEMPTS = 3;

    private DeliveryRules() {}

    /** status is null when nothing has been tried yet. A manual re-send of everything sends again even after SENT. */
    public static boolean shouldSend(String status, int attempts, boolean resendAll, boolean automatic) {
        if (status == null) return true;
        if (resendAll) return true;
        if ("SENT".equals(status)) return false;
        if ("FAILED".equals(status)) return !automatic || attempts < MAX_AUTO_ATTEMPTS;
        return true; // NO_EMAIL: look again, an address may have been added
    }

    /** The automatic job can mark a run done once nothing it would still retry is left failing. */
    public static boolean runIsSettled(int failedStillRetryable) {
        return failedStillRetryable == 0;
    }
}

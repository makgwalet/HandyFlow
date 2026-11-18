package za.co.handyflow.platform.fuel.dto;

/** What the two policy switches would have done to deliveries completed over a recent window. Counts only, no names. */
public final class EnforcementDtos {
    private EnforcementDtos() {}

    /**
     * delivered: loads completed in the window. wouldRefuseNoReceiver: completed with no receiver name and no exception used (POD policy).
     * wouldRefuseNoArrivalCheck: completed with no arrival check on record (arrival check policy); a stale or blocked check is not counted,
     * because the check history does not say what the load's check looked like at completion.
     */
    public record Readiness(int days, boolean arrivalCheckEnforced, boolean podPolicyEnforced, long delivered,
                            long wouldRefuseNoReceiver, long wouldRefuseNoArrivalCheck) {}
}

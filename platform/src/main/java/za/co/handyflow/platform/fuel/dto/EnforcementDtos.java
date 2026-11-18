package za.co.handyflow.platform.fuel.dto;

/** What the two policy switches would have done to deliveries completed over a recent window. Counts only, no names. */
public final class EnforcementDtos {
    private EnforcementDtos() {}

    /**
     * delivered: loads completed in the window. wouldRefuseNoReceiver: completed with no receiver name and no exception used (POD policy).
     * wouldRefuseNoSignature: completed with a receiver name but no signature and no exception used (the signature rule).
     * wouldRefuseNoArrivalCheck: completed with no arrival check on record (arrival check policy); a stale or blocked check is not counted,
     * because the check history does not say what the load's check looked like at completion.
     */
    public record Readiness(int days, boolean arrivalCheckEnforced, boolean podPolicyEnforced, long delivered,
                            long wouldRefuseNoReceiver, long wouldRefuseNoArrivalCheck, long wouldRefuseNoSignature) {}

    /** One control's rollout state. nextStates are the states it can move to now; observations are what it would have refused in the last 7 days while only watching. */
    public record ControlState(String control, String state, java.util.List<java.util.UUID> pilotDepotIds, String reason, java.time.Instant stateSince,
                               int version, java.util.UUID changedBy, long observationsLast7Days, java.util.List<String> nextStates) {}

    public record ChangeRequest(@jakarta.validation.constraints.NotBlank String state, java.util.List<java.util.UUID> pilotDepotIds,
                                @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 5, max = 500) String reason) {}

    public record Blockers(String control, String target, boolean allowed, java.util.List<String> blockers) {}

    public record Observation(java.util.UUID deliveryId, String message, java.time.Instant at) {}
}

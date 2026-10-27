package za.co.handyflow.platform.facilitiesmanagement.dto;

/**
 * {@code pauseSla} stops the SLA resolution clock while the work is on hold (for example when waiting on the
 * client for access or approval). It needs the SLA_MANAGE, MANAGE or ADMIN authority — a technician's plain hold
 * never pauses the clock — and the pause is audited. The clock resumes when work restarts.
 */
public record HoldFmWorkOrderRequest(String reason, boolean pauseSla) {}
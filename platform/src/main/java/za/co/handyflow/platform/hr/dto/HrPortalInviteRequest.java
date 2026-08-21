package za.co.handyflow.platform.hr.dto;

/**
 * inviteEmail is optional — when omitted, HrEmployeePortalAuthService
 * defaults to the employee's own email already on file. Given explicitly
 * only when HR wants to invite to a different address than the one on
 * record (e.g. a personal email, if the work email isn't checked yet on
 * day one).
 */
public record HrPortalInviteRequest(String inviteEmail) {}
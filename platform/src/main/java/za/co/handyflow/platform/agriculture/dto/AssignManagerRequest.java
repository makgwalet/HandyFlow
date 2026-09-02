package za.co.handyflow.platform.agriculture.dto;

import java.util.UUID;

/**
 * managerId only — managerName is never taken from the caller. The service
 * resolves it via {@code HrFacade.findEmployeeById()} and snapshots the
 * display name at write time, matching {@code AgHealthEvent.administeredBy}'s
 * own validated-employee-plus-name-snapshot convention.
 */
public record AssignManagerRequest(UUID managerId) {}

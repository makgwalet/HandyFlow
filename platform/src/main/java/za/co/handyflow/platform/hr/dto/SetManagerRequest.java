package za.co.handyflow.platform.hr.dto;

import java.util.UUID;

/** A null managerId removes the manager. */
public record SetManagerRequest(UUID managerId) {}

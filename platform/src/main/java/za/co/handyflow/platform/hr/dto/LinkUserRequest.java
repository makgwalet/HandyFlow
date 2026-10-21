package za.co.handyflow.platform.hr.dto;

import java.util.UUID;

/** A null userId removes the link between the employee and a staff login. */
public record LinkUserRequest(UUID userId) {}

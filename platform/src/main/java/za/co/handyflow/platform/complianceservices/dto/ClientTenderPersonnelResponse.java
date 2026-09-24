package za.co.handyflow.platform.complianceservices.dto;

import java.time.Instant;
import java.util.UUID;

/** employeeFullName/employeeNumber are looked up LIVE from HrFacade at read time — never stored. */
public record ClientTenderPersonnelResponse(
        UUID id, UUID clientTenderId, UUID employeeId, String role,
        boolean employeeFound, String employeeFullName, String employeeNumber, Instant createdAt
) {}

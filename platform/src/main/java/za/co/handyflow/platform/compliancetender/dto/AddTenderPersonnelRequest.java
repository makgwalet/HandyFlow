package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Either an HR employee (employeeId), or someone outside HR (personType DIRECTOR, SUBCONTRACTOR, CONSULTANT or OTHER, with a name
 * and optionally their organisation). Exactly one of the two.
 */
public record AddTenderPersonnelRequest(UUID employeeId, @NotBlank String role,
                                        String personType, String externalName, String externalOrganisation) {}

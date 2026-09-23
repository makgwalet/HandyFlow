package za.co.handyflow.platform.compliancetender.application;

import za.co.handyflow.platform.compliancetender.dto.ComplianceRegistrationResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The public contract other modules depend on — mirrors the established
 * HrFacade / CrmFacade / EvidenceFacade shape (a narrow interface in
 * application/, implementation kept in application/internal/, so a
 * calling module never depends on this module's internal service class
 * directly).
 * <p>
 * Deliberately narrow for Phase 1 — read-only, registrations only. The
 * two concrete future consumers this is scoped for: {@code
 * complianceservices} (the external module, not yet started, depending
 * on this module per the strategic roadmap backlog's own architecture)
 * and a later tender-readiness check inside this same module once Phase
 * 2 starts. Writes stay behind {@code ComplianceRegistrationService} and
 * the controller for now — a facade write method gets added when a real
 * second module actually needs one, not speculatively.
 */
public interface ComplianceFacade {

    List<ComplianceRegistrationResponse> findRegistrations(TenantId tenantId);

    Optional<ComplianceRegistrationResponse> findRegistration(TenantId tenantId, UUID id);
}

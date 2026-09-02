package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing reads — everything a logged-in portal user (acting for
 * one {@link TrainProvClient}) is allowed to see: their own delegates,
 * enrollments and certificates, never another client's. Every method
 * here resolves and enforces the caller's own clientId(s) from their
 * ACCEPTED grants first — never trusts a clientId the frontend might
 * pass in directly.
 */
@Service
@RequiredArgsConstructor
public class TrainProvPortalDataService {

    private final TrainProvPortalAccessGrantRepository grantRepo;
    private final TrainProvDelegateRepository delegateRepo;
    private final TrainProvEnrollmentRepository enrollmentRepo;
    private final TrainProvCertificateRepository certificateRepo;
    private final TrainProvInvoiceRepository invoiceRepo;

    public List<TrainProvDelegate> getMyDelegates(TenantId tenantId, UUID portalUserId) {
        UUID clientId = resolveClientId(portalUserId);
        return delegateRepo.findAllActiveForClient(tenantId, clientId);
    }

    public Page<TrainProvEnrollment> getMyEnrollments(TenantId tenantId, UUID portalUserId, String status, Pageable pageable) {
        UUID clientId = resolveClientId(portalUserId);
        return enrollmentRepo.findAll(tenantId, null, clientId, status, pageable);
    }

    public Page<TrainProvCertificate> getMyCertificates(TenantId tenantId, UUID portalUserId, String status, Pageable pageable) {
        UUID clientId = resolveClientId(portalUserId);
        return certificateRepo.findAll(tenantId, clientId, status, pageable);
    }

    public List<TrainProvInvoice> getMyInvoices(TenantId tenantId, UUID portalUserId) {
        UUID clientId = resolveClientId(portalUserId);
        return invoiceRepo.findAllForClientList(tenantId, clientId);
    }

    /**
     * A portal user in this first pass is linked to exactly one
     * client — if they somehow hold more than one ACCEPTED grant (not
     * currently reachable through this module's own invite flow, which
     * always creates one grant per client), the first is used and the
     * rest are ignored rather than merging cross-client data. Flagged
     * in the status doc as a known first-pass limit, not a security
     * gap: nothing here fabricates access to a client the caller
     * wasn't actually granted.
     */
    private UUID resolveClientId(UUID portalUserId) {
        return grantRepo.findAcceptedByPortalUser(portalUserId).stream()
                .findFirst()
                .map(TrainProvPortalAccessGrant::getClientId)
                .orElseThrow(() -> new HandyFlowException(
                        "No active client access found for this portal account", HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS"));
    }
}

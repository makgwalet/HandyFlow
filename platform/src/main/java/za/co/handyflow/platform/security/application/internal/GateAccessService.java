// security/application/internal/GateAccessService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.domain.model.AccessPoint;
import za.co.handyflow.platform.security.domain.model.GateRegisterEntry;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.AccessPointRepository;
import za.co.handyflow.platform.security.domain.repository.GateRegisterEntryRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GateAccessService — Gate Access &amp; Registry sub-module.
 * <p>
 * Step 2: guard-facing logArrival()/logExit(). Step 3 (this pass):
 * supervisor-facing AccessPoint CRUD, the on-site list, and gate-log
 * history — all reached only through the tenant-JWT surface
 * (/api/v1/security/**), never the guard token surface. No ADMIN-tier
 * action anywhere in this file: AccessPoint has no hard-delete concept
 * in this design, only active/inactive, so nothing here rises to the
 * "genuinely hard to undo" bar that would warrant SECURITY_ADMIN over
 * SECURITY_MANAGE, matching this module's own established
 * segregation-of-duties convention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GateAccessService {

    private final AccessPointRepository        accessPointRepository;
    private final GateRegisterEntryRepository  entryRepository;
    private final GuardRepository              guardRepository;
    private final DeviceSessionService         deviceSessionService;
    private final EvidenceFacade               evidenceFacade;

    /**
     * FIX: mobile addendum rule #3 — the three genuinely distinct
     * capture types, not one generic "scan". Kept as validated
     * plain-String docType values, matching this feature's own
     * established convention (entryType, status, idScanConfidence are
     * all plain validated Strings, not Java enums) rather than
     * importing a different module's enum-based convention.
     */
    private static final Set<String> VALID_ATTACHMENT_DOC_TYPES =
            Set.of("ID_DOCUMENT", "VEHICLE_DISC", "GENERAL_PHOTO");

    // ── Guard-facing: arrival ────────────────────────────────────────────────

    @Transactional
    public GateRegisterEntryResponse logArrival(TenantId tenantId, LogArrivalRequest req) {
        AccessPoint accessPoint = accessPointRepository.findByIdAndTenant(req.accessPointId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessPoint", req.accessPointId().toString()));
        if (!accessPoint.isActive()) {
            throw new HandyFlowException("This access point is not active",
                    HttpStatus.BAD_REQUEST, "ACCESS_POINT_INACTIVE");
        }

        UUID guardId = resolveActingGuard(tenantId, req.deviceHardwareId());

        GateRegisterEntry entry = GateRegisterEntry.logArrival(
                tenantId, accessPoint.getSiteId(), accessPoint.getId(),
                req.entryType(), req.personName(), req.idNumber(), req.phone(), req.company(),
                req.hostName(), req.hostContact(), req.purpose(),
                req.vehicleRegistration(), req.vehicleMakeModel(), req.driverName(),
                req.idScanConfidence(), guardId);
        entryRepository.save(entry);

        log.info("[Security] Gate entry logged id={} accessPoint={} type={} guard={}",
                entry.getId(), accessPoint.getId(), req.entryType(), guardId);

        return toResponse(entry, accessPoint);
    }

    // ── Guard-facing: exit ───────────────────────────────────────────────────

    @Transactional
    public GateRegisterEntryResponse logExit(TenantId tenantId, UUID entryId, LogExitRequest req) {
        GateRegisterEntry entry = entryRepository.findByIdAndTenant(entryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GateRegisterEntry", entryId.toString()));

        UUID guardId = resolveActingGuard(tenantId, req.deviceHardwareId());

        entry.logExit(guardId);
        entryRepository.save(entry);

        log.info("[Security] Gate entry departed id={} guard={}", entryId, guardId);

        AccessPoint accessPoint = accessPointRepository.findById(entry.getAccessPointId()).orElse(null);
        return toResponse(entry, accessPoint);
    }

    // ── Supervisor-facing: AccessPoint CRUD ──────────────────────────────────

    @Transactional
    public AccessPointResponse createAccessPoint(TenantId tenantId, CreateAccessPointRequest req) {
        AccessPoint a = AccessPoint.create(tenantId, req.siteId(), req.name(), req.description());
        accessPointRepository.save(a);
        log.info("[Security] Access point created id={} site={} name={}", a.getId(), req.siteId(), req.name());
        return toAccessPointResponse(a);
    }

    @Transactional(readOnly = true)
    public List<AccessPointResponse> getAccessPoints(TenantId tenantId, UUID siteId) {
        return accessPointRepository.findActiveBySite(tenantId, siteId).stream()
                .map(this::toAccessPointResponse).toList();
    }

    @Transactional
    public AccessPointResponse updateAccessPoint(TenantId tenantId, UUID id, UpdateAccessPointRequest req) {
        AccessPoint a = findAccessPoint(tenantId, id);
        a.update(req.name(), req.description());
        accessPointRepository.save(a);
        return toAccessPointResponse(a);
    }

    @Transactional
    public AccessPointResponse deactivateAccessPoint(TenantId tenantId, UUID id) {
        AccessPoint a = findAccessPoint(tenantId, id);
        a.deactivate();
        accessPointRepository.save(a);
        log.info("[Security] Access point deactivated id={}", id);
        return toAccessPointResponse(a);
    }

    @Transactional
    public AccessPointResponse reactivateAccessPoint(TenantId tenantId, UUID id) {
        AccessPoint a = findAccessPoint(tenantId, id);
        a.reactivate();
        accessPointRepository.save(a);
        return toAccessPointResponse(a);
    }

    // ── Supervisor-facing: on-site list + gate log ───────────────────────────

    /**
     * Backs both the supervisor "who's on site" view and the client
     * portal's currentlyOnSite extension (Step 5) — same read model,
     * two callers.
     */
    @Transactional(readOnly = true)
    public List<GateRegisterEntryResponse> getOnSite(TenantId tenantId, UUID siteId) {
        List<GateRegisterEntry> entries = entryRepository.findOnSiteBySite(tenantId, siteId);
        return entries.stream().map(e -> toResponse(e, resolveAccessPointOrNull(e))).toList();
    }

    @Transactional(readOnly = true)
    public Page<GateRegisterEntryResponse> getGateLog(TenantId tenantId, UUID siteId,
                                                      Instant from, Instant to, Pageable pageable) {
        return entryRepository.findBySiteAndPeriod(tenantId, siteId, from, to, pageable)
                .map(e -> toResponse(e, resolveAccessPointOrNull(e)));
    }

    // ── Guard-facing: evidence attachment ────────────────────────────────────

    /**
     * FIX: mobile addendum rule #3 — was designed for (idScanConfidence
     * on the entity, no photoUrl column) but never actually wired up.
     * Same EvidenceFacade pattern already proven for Payroll Bureau's
     * logo attachments, Recruitment Agency's CVs, and this session's
     * own RFI attachments. Identity resolved the same way as
     * logArrival()/logExit() — never a client-supplied guard field.
     */
    @Transactional
    public EvidenceResponse attachEvidence(TenantId tenantId, UUID entryId, MultipartFile file,
                                           String docType, String deviceHardwareId) {
        GateRegisterEntry entry = entryRepository.findByIdAndTenant(entryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GateRegisterEntry", entryId.toString()));

        if (!VALID_ATTACHMENT_DOC_TYPES.contains(docType)) {
            throw new HandyFlowException(
                    "docType must be one of: " + VALID_ATTACHMENT_DOC_TYPES,
                    HttpStatus.BAD_REQUEST, "INVALID_DOC_TYPE");
        }

        UUID guardId = resolveActingGuard(tenantId, deviceHardwareId);
        Guard guard = guardRepository.findById(guardId).orElse(null);
        String guardName = guard != null ? guard.getFullName() : "Unknown guard";

        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, docType,
                "security", "GateRegisterEntry", entry.getId(), null, guardId, guardName);

        log.info("[Security] Evidence attached entry={} docType={} guard={}", entryId, docType, guardId);
        return evidence;
    }

    // ── Shared: list attachments — used by both guard app review and supervisor view ──

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getAttachments(TenantId tenantId, UUID entryId) {
        GateRegisterEntry entry = entryRepository.findByIdAndTenant(entryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GateRegisterEntry", entryId.toString()));
        return evidenceFacade.listFor(tenantId, "security", "GateRegisterEntry", entry.getId());
    }

    // ── Identity resolution ──────────────────────────────────────────────────

    /**
     * FIX: same identity-spoofing fix already applied throughout this
     * module (CheckpointScanController's guardId, IncidentController) —
     * the acting guard is always resolved from a genuinely open
     * DeviceSession, never trusted from the request body or the JWT
     * subject alone. A guard's JWT can still be technically valid
     * moments after they've clocked out; this catches that case.
     */
    private UUID resolveActingGuard(TenantId tenantId, String deviceHardwareId) {
        return deviceSessionService.resolveGuardId(deviceHardwareId, tenantId)
                .orElseThrow(() -> new HandyFlowException(
                        "No open guard session found on this device — clock in before logging a gate entry",
                        HttpStatus.FORBIDDEN, "NO_OPEN_SESSION"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AccessPoint findAccessPoint(TenantId tenantId, UUID id) {
        return accessPointRepository.findByIdAndTenant(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessPoint", id.toString()));
    }

    private AccessPoint resolveAccessPointOrNull(GateRegisterEntry e) {
        return accessPointRepository.findById(e.getAccessPointId()).orElse(null);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Masks idNumber before it ever leaves the backend — enforced here,
     * not left to the frontend, same posture as GuardService.toResponse()'s
     * own confirmed reasoning. Duplicates GuardService.maskIdNumber()'s
     * exact algorithm rather than introducing a shared utility for one
     * small, pure, stateless method — same "duplication over coupling for
     * small self-contained helpers" precedent used elsewhere this session.
     */
    private String maskIdNumber(String idNumber) {
        if (idNumber == null) return null;
        String digitsOnly = idNumber.replaceAll("\\D", "");
        if (digitsOnly.length() != 13) return idNumber; // not an SA ID shape -- leave as-is
        return digitsOnly.substring(0, 6) + "*".repeat(digitsOnly.length() - 6);
    }

    private GateRegisterEntryResponse toResponse(GateRegisterEntry e, AccessPoint accessPoint) {
        Guard loggedInBy  = guardRepository.findById(e.getLoggedInByGuardId()).orElse(null);
        Guard loggedOutBy = e.getLoggedOutByGuardId() != null
                ? guardRepository.findById(e.getLoggedOutByGuardId()).orElse(null) : null;

        return new GateRegisterEntryResponse(
                e.getId(), e.getSiteId(), e.getAccessPointId(),
                accessPoint != null ? accessPoint.getName() : null,
                e.getEntryType(),
                e.getPersonName(), maskIdNumber(e.getIdNumber()), e.getPhone(), e.getCompany(),
                e.getHostName(), e.getHostContact(), e.getPurpose(),
                e.getVehicleRegistration(), e.getVehicleMakeModel(), e.getDriverName(),
                e.getIdScanConfidence(),
                e.getLoggedInByGuardId(), loggedInBy != null ? loggedInBy.getFullName() : null, e.getLoggedInAt(),
                e.getLoggedOutByGuardId(), loggedOutBy != null ? loggedOutBy.getFullName() : null, e.getLoggedOutAt(),
                e.getStatus(), e.getCreatedAt());
    }

    private AccessPointResponse toAccessPointResponse(AccessPoint a) {
        return new AccessPointResponse(a.getId(), a.getSiteId(), a.getName(),
                a.getDescription(), a.isActive(), a.getCreatedAt());
    }
}
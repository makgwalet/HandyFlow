package za.co.handyflow.platform.controls.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.controls.domain.model.ControlException;
import za.co.handyflow.platform.controls.domain.repository.ControlExceptionRepository;
import za.co.handyflow.platform.controls.dto.ControlExceptionDetail;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What a person does with an exception: rate it, give it an owner and a date, record management's response, close it, and check later that the
 * fix held. The rules live on {@link ControlException}; this class adds the checks that need other modules (evidence) and writes every step to the audit trail.
 */
@Service
@RequiredArgsConstructor
public class ControlExceptionManagementService {

    public static final String EVIDENCE_MODULE = "controls";
    public static final String EVIDENCE_ENTITY = "CONTROL_EXCEPTION";

    private final ControlExceptionRepository repo;
    private final EvidenceFacade evidence;
    private final AuditTrailFacade trail;
    private final UserRepository userRepo;

    /** scope: open (default), follow-ups (resolved and waiting for a check), all. */
    @Transactional(readOnly = true)
    public List<ControlExceptionDetail> list(TenantId tenant, String scope, LocalDate today) {
        String s = scope == null ? "open" : scope.toLowerCase();
        return repo.findAllByTenant(tenant.getValue()).stream()
                .filter(e -> switch (s) {
                    case "all" -> true;
                    case "follow-ups" -> e.followUpDueOn(today) || ("RESOLVED".equals(e.getStatus()) && e.getFollowUpDate() != null && e.getFollowUpAt() == null);
                    case "open" -> "OPEN".equals(e.getStatus());
                    default -> throw new HandyFlowException("Scope must be open, follow-ups or all", HttpStatus.BAD_REQUEST, "CONTROL_EXCEPTION_SCOPE");
                })
                .map(e -> toDetail(tenant, e, today)).toList();
    }

    @Transactional
    public ControlExceptionDetail rate(TenantId tenant, UUID id, UUID by, String byName, String rating, String rationale, LocalDate today) {
        ControlException e = load(tenant, id);
        Map<String, Object> before = snapshot(e);
        guard(() -> e.rate(by, byName, rating, rationale));
        return saved(tenant, e, "CONTROL_EXCEPTION_RATED", before, today);
    }

    @Transactional
    public ControlExceptionDetail assign(TenantId tenant, UUID id, UUID ownerId, LocalDate targetDate, LocalDate today) {
        ControlException e = load(tenant, id);
        String ownerName = ownerId == null ? null : userRepo.findByIdAndTenantId(ownerId, tenant)
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .orElseThrow(() -> new HandyFlowException("That person is not in this company", HttpStatus.BAD_REQUEST, "CONTROL_EXCEPTION_OWNER"));
        Map<String, Object> before = snapshot(e);
        guard(() -> e.assign(ownerId, ownerName, targetDate));
        return saved(tenant, e, "CONTROL_EXCEPTION_ASSIGNED", before, today);
    }

    @Transactional
    public ControlExceptionDetail respond(TenantId tenant, UUID id, UUID by, String byName, String text, LocalDate today) {
        ControlException e = load(tenant, id);
        Map<String, Object> before = snapshot(e);
        guard(() -> e.respond(by, byName, text));
        return saved(tenant, e, "CONTROL_EXCEPTION_RESPONDED", before, today);
    }

    @Transactional
    public ControlExceptionDetail resolve(TenantId tenant, UUID id, UUID by, String byName, String notes, LocalDate followUpOn, LocalDate today) {
        ControlException e = load(tenant, id);
        if (e.isSerious() && evidence.listFor(tenant, EVIDENCE_MODULE, EVIDENCE_ENTITY, e.getId()).isEmpty()) {
            throw new HandyFlowException("Attach at least one piece of evidence before resolving a "
                    + e.getRiskRating().toLowerCase() + "-risk exception", HttpStatus.CONFLICT, "CONTROL_EXCEPTION_EVIDENCE_REQUIRED");
        }
        Map<String, Object> before = snapshot(e);
        guard(() -> e.resolve(by, byName, notes, followUpOn, today));
        return saved(tenant, e, "CONTROL_EXCEPTION_RESOLVED", before, today);
    }

    @Transactional
    public ControlExceptionDetail completeFollowUp(TenantId tenant, UUID id, UUID by, String byName, String outcome, String note, LocalDate today) {
        ControlException e = load(tenant, id);
        Map<String, Object> before = snapshot(e);
        guard(() -> e.completeFollowUp(by, byName, outcome, note));
        return saved(tenant, e, "CONTROL_EXCEPTION_FOLLOWED_UP", before, today);
    }

    // ----------------------------------------------------------------------------------------------------------------

    private ControlException load(TenantId tenant, UUID id) {
        return repo.findByTenantIdAndId(tenant.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Exception not found", HttpStatus.NOT_FOUND, "CONTROL_EXCEPTION_NOT_FOUND"));
    }

    private ControlExceptionDetail saved(TenantId tenant, ControlException e, String action, Map<String, Object> before, LocalDate today) {
        repo.save(e);
        trail.record(tenant, action, "CONTROL_EXCEPTION", e.getId(), before, snapshot(e));
        return toDetail(tenant, e, today);
    }

    private static void guard(Runnable r) {
        try { r.run(); }
        catch (IllegalArgumentException ex) { throw new HandyFlowException(ex.getMessage(), HttpStatus.BAD_REQUEST, "CONTROL_EXCEPTION_INVALID"); }
        catch (IllegalStateException ex) { throw new HandyFlowException(ex.getMessage(), HttpStatus.CONFLICT, "CONTROL_EXCEPTION_STATE"); }
    }

    private static Map<String, Object> snapshot(ControlException e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", e.getStatus());
        m.put("riskRating", e.getRiskRating());
        m.put("ownerName", e.getOwnerName());
        m.put("targetDate", e.getTargetDate() == null ? null : e.getTargetDate().toString());
        m.put("hasResponse", e.getResponseText() != null);
        m.put("followUpDate", e.getFollowUpDate() == null ? null : e.getFollowUpDate().toString());
        m.put("followUpOutcome", e.getFollowUpOutcome());
        m.put("timesReopened", e.getTimesReopened());
        return m;
    }

    private ControlExceptionDetail toDetail(TenantId tenant, ControlException e, LocalDate today) {
        int files = evidence.listFor(tenant, EVIDENCE_MODULE, EVIDENCE_ENTITY, e.getId()).size();
        return new ControlExceptionDetail(e.getId(), e.getSourceModule(), e.getControlType(), e.getRelatedEntityType(), e.getRelatedEntityId(),
                e.getSeverity(), e.getDescription(), e.getStatus(), e.getDetectedAt(),
                e.getRiskRating(), e.getRiskRationale(), e.getRatedByName(), e.getRatedAt(),
                e.getOwnerId(), e.getOwnerName(), e.getTargetDate(), e.overdueOn(today),
                e.getResponseText(), e.getResponseByName(), e.getResponseAt(),
                e.getResolvedByName(), e.getResolvedAt(), e.getResolutionNotes(),
                e.getFollowUpDate(), e.followUpDueOn(today), e.getFollowUpAt(), e.getFollowUpByName(),
                e.getFollowUpOutcome(), e.getFollowUpNote(), e.getTimesReopened(), files);
    }
}

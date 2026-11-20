package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderAssignmentRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderClientRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderSubmissionRepository;
import za.co.handyflow.platform.legalcompliance.dto.SubmissionDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The provider's side of submissions. A provider never writes into a client's registers: it can only add a row to the client's submissions inbox, and only when the client granted SUBMIT.
 *
 * Every method starts from the provider's OWN relationship row (found by the caller's tenant), so another provider's relationship id is "not found". The cross-tenant write is the single
 * insert in {@link #submit}; reads and withdrawals are filtered by provider tenant AND relationship id, so a provider sees only what it sent through that relationship.
 * The gate is the same one used for reading: relationship ACTIVE, scope granted, and the caller a manager or actively assigned. Each action is audited on both trails.
 */
@Service
@RequiredArgsConstructor
public class ProviderSubmissionService {

    private static final int PAGE = 200;

    private final ProviderClientRepository clients;
    private final ProviderAssignmentRepository assignments;
    private final ProviderSubmissionRepository submissions;
    private final RegulatoryAuditService trail;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    @Transactional(readOnly = true)
    public List<SubmissionView> list(TenantId t, UUID relationshipId, UUID uid, boolean manager) {
        ProviderClient c = authorised(t, relationshipId, uid, manager);
        LocalDate today = today();
        return submissions.listForProvider(t.getValue(), c.getId(), PageRequest.of(0, PAGE)).stream().map(s -> SubmissionRules.view(s, today)).toList();
    }

    @Transactional
    public SubmissionView submit(TenantId t, UUID relationshipId, SubmitRequest r, UUID uid, String uname, boolean manager) {
        ProviderClient c = authorised(t, relationshipId, uid, manager);
        if (SubmissionRules.limitReached(submissions.countUnansweredForProvider(t.getValue(), c.getId())))
            throw new HandyFlowException("This client already has " + SubmissionRules.OPEN_LIMIT + " submissions waiting for an answer. Wait for them to be answered first", HttpStatus.CONFLICT, "INVALID_STATE");
        ProviderSubmission s = guarded(() -> ProviderSubmission.send(TenantId.of(c.getClientTenantId()), c.getId(), t.getValue(), c.getProviderName(), String.format("PSB-%05d", submissions.nextRef()),
                r.kind(), r.area(), r.priority(), r.title(), r.body(), r.respondBy(), today(), uid, uname));
        submissions.save(s);
        String what = s.getReference() + " " + s.getKind().name().toLowerCase().replace('_', ' ') + " (" + s.getArea().name().toLowerCase() + ")";
        trail.record(c.getClientTenantId(), ProviderService.ACCESS_ENTITY, c.getId(), "SUBMISSION_SENT", c.getProviderName() + " sent " + what, uid, uname);
        trail.record(t.getValue(), ProviderService.ENTITY, c.getId(), "SUBMISSION_SENT", "Sent " + what, uid, uname);
        return SubmissionRules.view(s, today());
    }

    @Transactional
    public SubmissionView withdraw(TenantId t, UUID relationshipId, UUID submissionId, UUID uid, String uname, boolean manager) {
        ProviderClient c = authorised(t, relationshipId, uid, manager);
        ProviderSubmission s = submissions.findForProvider(t.getValue(), c.getId(), submissionId).orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId.toString()));
        guarded(() -> { s.withdraw(); return null; });
        submissions.save(s);
        trail.record(c.getClientTenantId(), ProviderService.ACCESS_ENTITY, c.getId(), "SUBMISSION_WITHDRAWN", c.getProviderName() + " withdrew " + s.getReference(), uid, uname);
        trail.record(t.getValue(), ProviderService.ENTITY, c.getId(), "SUBMISSION_WITHDRAWN", "Withdrew " + s.getReference(), uid, uname);
        return SubmissionRules.view(s, today());
    }

    /** Managers only (the controller enforces it). The client decides whether to grant it; this only records the ask. */
    @Transactional
    public void requestScopes(TenantId t, UUID relationshipId, ScopesRequest r, UUID uid, String uname) {
        ProviderClient c = clients.findForTenant(t, relationshipId).orElseThrow(() -> new ResourceNotFoundException("Client", relationshipId.toString()));
        guarded(() -> { c.requestMore(r.scopes()); return null; });
        clients.save(c);
        String asked = ProviderRules.scopesToCsv(r.scopes()).toLowerCase();
        trail.record(t.getValue(), ProviderService.ENTITY, c.getId(), "ACCESS_REQUESTED", "Asked the client for " + asked, uid, uname);
        if (c.getClientTenantId() != null)
            trail.record(c.getClientTenantId(), ProviderService.ACCESS_ENTITY, c.getId(), "ACCESS_REQUESTED", c.getProviderName() + " asked for " + asked, uid, uname);
    }

    /** The single gate: the caller's own relationship, ACTIVE, SUBMIT granted, and a manager or an assigned consultant. */
    private ProviderClient authorised(TenantId t, UUID relationshipId, UUID uid, boolean manager) {
        ProviderClient c = clients.findForTenant(t, relationshipId).orElseThrow(() -> new ResourceNotFoundException("Client", relationshipId.toString()));
        boolean assigned = manager || assignments.findActive(t, c.getId(), uid).isPresent();
        if (!manager && !assigned) throw new ResourceNotFoundException("Client", relationshipId.toString());
        String why = ProviderRules.denial(c.getStatus(), c.scopesGranted(), AccessScope.SUBMIT, manager, assigned);
        if (why != null || c.getClientTenantId() == null)
            throw new HandyFlowException(why != null ? why : "Access to this client is not currently active", HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        return c;
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}

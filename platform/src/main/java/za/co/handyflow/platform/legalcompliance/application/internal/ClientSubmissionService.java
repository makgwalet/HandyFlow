package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderClient;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderSubmission;
import za.co.handyflow.platform.legalcompliance.domain.model.SubmissionStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.AccessScope;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderClientRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderSubmissionRepository;
import za.co.handyflow.platform.legalcompliance.dto.ProviderDtos.ProviderAccessResponse;
import za.co.handyflow.platform.legalcompliance.dto.SubmissionDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * The client's side: the inbox of what providers sent, and the choice of what each provider may send. Everything is scoped to the caller's own tenant. Answering a submission never changes
 * any other record; if the client acts on it, it does so in the normal registers, as itself. The reply is visible to the provider, so it must not contain anything the client wants to keep private.
 */
@Service
@RequiredArgsConstructor
public class ClientSubmissionService {

    private static final int PAGE = 200;

    private final ProviderSubmissionRepository submissions;
    private final ProviderClientRepository clients;
    private final RegulatoryAuditService trail;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    @Transactional(readOnly = true)
    public Inbox inbox(TenantId client, SubmissionStatus status) {
        LocalDate today = today();
        return new Inbox(submissions.countOpenForClient(client), submissions.searchForClient(client, status, PageRequest.of(0, PAGE)).stream().map(s -> SubmissionRules.view(s, today)).toList());
    }

    @Transactional(readOnly = true)
    public SubmissionView get(TenantId client, UUID id) { return SubmissionRules.view(load(client, id), today()); }

    @Transactional
    public SubmissionView accept(TenantId client, UUID id, RespondRequest r, UUID uid, String uname) {
        ProviderSubmission s = load(client, id);
        guarded(() -> { s.accept(r.note(), uname, Instant.now()); return null; });
        return saved(client, s, "ACCEPTED", "accepted", uid, uname);
    }

    @Transactional
    public SubmissionView decline(TenantId client, UUID id, RespondRequest r, UUID uid, String uname) {
        ProviderSubmission s = load(client, id);
        guarded(() -> { s.decline(r.note(), uname, Instant.now()); return null; });
        return saved(client, s, "DECLINED", "declined", uid, uname);
    }

    @Transactional
    public SubmissionView complete(TenantId client, UUID id, RespondRequest r, UUID uid, String uname) {
        ProviderSubmission s = load(client, id);
        guarded(() -> { s.complete(r.note(), uname, Instant.now()); return null; });
        return saved(client, s, "COMPLETED", "completed", uid, uname);
    }

    /** Changes what a provider may do in this workspace, within what it asked for. Taking everything away is "end the access". */
    @Transactional
    public ProviderAccessResponse changeGrants(TenantId client, UUID relationshipId, ScopesRequest r, UUID uid, String uname) {
        ProviderClient c = clients.findForClientTenantAcrossTenants(client.getValue(), relationshipId).orElseThrow(() -> new ResourceNotFoundException("Provider access", relationshipId.toString()));
        boolean hadSubmit = c.scopesGranted().contains(AccessScope.SUBMIT);
        guarded(() -> { c.changeGrants(r.scopes()); return null; });
        clients.save(c);
        String now = c.getGrantedScopes().toLowerCase();
        trail.record(client.getValue(), ProviderService.ACCESS_ENTITY, c.getId(), "GRANTS_CHANGED", "Access for " + c.getProviderName() + " is now: " + now + (hadSubmit && !c.scopesGranted().contains(AccessScope.SUBMIT) ? " (submissions switched off)" : ""), uid, uname);
        trail.record(c.getTenantId().getValue(), ProviderService.ENTITY, c.getId(), "GRANTS_CHANGED", "The client changed what it grants: " + now, uid, uname);
        return ClientAccessService.response(c);
    }

    private SubmissionView saved(TenantId client, ProviderSubmission s, String event, String verb, UUID uid, String uname) {
        submissions.save(s);
        trail.record(client.getValue(), ProviderService.ACCESS_ENTITY, s.getProviderClientId(), "SUBMISSION_" + event, s.getReference() + " " + verb, uid, uname);
        trail.record(s.getProviderTenantId(), ProviderService.ENTITY, s.getProviderClientId(), "SUBMISSION_" + event, "The client " + verb + " " + s.getReference(), uid, uname);
        return SubmissionRules.view(s, today());
    }

    private ProviderSubmission load(TenantId client, UUID id) {
        return submissions.findForClient(client, id).orElseThrow(() -> new ResourceNotFoundException("Submission", id.toString()));
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}

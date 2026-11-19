package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.AccessSide;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderClient;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderClientStatus;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderClientRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.RegulatoryEventRepository;
import za.co.handyflow.platform.legalcompliance.dto.ProviderDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The client organisation's side of the relationship: it decides what a provider may see, and can pause or end that at any time.
 * Everything here is scoped to the caller's own tenant through client_tenant_id; a relationship that does not name the caller is "not found".
 * The one lookup that is not scoped is the invitation code (which is how a client first finds an invitation that cannot name it yet). Codes are single-use,
 * expire after {@value ProviderRules#INVITE_DAYS} days and only their hash is stored; a wrong, used or expired code all give the same generic answer so codes cannot be probed.
 * The provider's own notes, contract, team and reviews are never shown here.
 */
@Service("legalComplianceClientAccessService")
@RequiredArgsConstructor
public class ClientAccessService {

    private static final String BAD_CODE = "That invitation code is not valid. Check it with the provider, or ask them to send a new one";

    private final ProviderClientRepository clients;
    private final RegulatoryEventRepository events;
    private final RegulatoryAuditService trail;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    @Transactional(readOnly = true)
    public List<ProviderAccessResponse> list(TenantId client) {
        return clients.findByClientTenantAcrossTenants(client.getValue()).stream().map(ClientAccessService::response).toList();
    }

    /** Lets the client read who is asking and for what before deciding. Does not consume the code. */
    @Transactional(readOnly = true)
    public InvitePreview preview(TenantId client, String code) {
        ProviderClient c = findInvite(client, code);
        return new InvitePreview(c.getProviderName(), c.scopesRequested(), c.getInviteExpiresAt());
    }

    @Transactional
    public ProviderAccessResponse accept(TenantId client, AcceptRequest r, UUID uid, String uname) {
        ProviderClient c = findInvite(client, r.code());
        guarded(() -> { c.accept(client.getValue(), r.scopes(), uid, uname, Instant.now(), today()); return null; });
        try {
            clients.saveAndFlush(c);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // uq_lc_pc_link: this organisation is already linked to this provider
            throw new HandyFlowException("This organisation already has active access set up with that provider. End it first if you want to start again", HttpStatus.CONFLICT, "INVALID_STATE");
        }
        String granted = c.getGrantedScopes().toLowerCase();
        trail.record(client.getValue(), ProviderService.ACCESS_ENTITY, c.getId(), "ACCEPTED", "Access granted to " + c.getProviderName() + ": " + granted, uid, uname);
        trail.record(c.getTenantId().getValue(), ProviderService.ENTITY, c.getId(), "ACCEPTED", "Client accepted, granting " + granted, uid, uname);
        return response(c);
    }

    @Transactional
    public ProviderAccessResponse suspend(TenantId client, UUID id, ReasonRequest r, UUID uid, String uname) {
        ProviderClient c = load(client, id);
        guarded(() -> { c.suspend(AccessSide.CLIENT, r.reason()); return null; });
        clients.save(c);
        both(client, c, "SUSPENDED", "By the client: " + r.reason().trim(), uid, uname);
        return response(c);
    }

    @Transactional
    public ProviderAccessResponse resume(TenantId client, UUID id, UUID uid, String uname) {
        ProviderClient c = load(client, id);
        guarded(() -> { c.resume(AccessSide.CLIENT); return null; });
        clients.save(c);
        both(client, c, "RESUMED", "Access resumed by the client", uid, uname);
        return response(c);
    }

    @Transactional
    public ProviderAccessResponse revoke(TenantId client, UUID id, ReasonRequest r, UUID uid, String uname) {
        ProviderClient c = load(client, id);
        guarded(() -> { c.end(AccessSide.CLIENT, r.reason(), Instant.now()); return null; });
        clients.save(c);
        both(client, c, "ENDED", "By the client: " + r.reason().trim(), uid, uname);
        return response(c);
    }

    /** What the provider's people did in this workspace, newest first. Read from the client's own audit trail. */
    @Transactional(readOnly = true)
    public List<AccessEvent> activity(TenantId client, UUID id) {
        ProviderClient c = load(client, id);
        return events.forEntity(client.getValue(), ProviderService.ACCESS_ENTITY, c.getId()).stream()
                .map(e -> new AccessEvent(e.getCreatedAt(), e.getEventType(), e.getDetail(), e.getActorName())).toList();
    }

    private void both(TenantId client, ProviderClient c, String event, String detail, UUID uid, String uname) {
        trail.record(client.getValue(), ProviderService.ACCESS_ENTITY, c.getId(), event, detail, uid, uname);
        trail.record(c.getTenantId().getValue(), ProviderService.ENTITY, c.getId(), event, detail, uid, uname);
    }

    private ProviderClient findInvite(TenantId client, String code) {
        if (!ProviderRules.looksLikeCode(code)) throw new HandyFlowException(BAD_CODE, HttpStatus.BAD_REQUEST, "VALIDATION");
        ProviderClient c = clients.findByInviteHashAcrossTenants(ProviderRules.hash(code)).orElse(null);
        if (c == null || c.getStatus() != ProviderClientStatus.INVITED || ProviderRules.inviteExpired(c.getInviteExpiresAt(), Instant.now()))
            throw new HandyFlowException(BAD_CODE, HttpStatus.BAD_REQUEST, "VALIDATION");
        if (c.getTenantId().getValue().equals(client.getValue())) throw new HandyFlowException("An organisation cannot be its own provider", HttpStatus.BAD_REQUEST, "VALIDATION");
        return c;
    }

    private ProviderClient load(TenantId client, UUID id) {
        return clients.findForClientTenantAcrossTenants(client.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Provider access", id.toString()));
    }

    static ProviderAccessResponse response(ProviderClient c) {
        return new ProviderAccessResponse(c.getId(), c.getProviderName(), c.getStatus(), c.scopesRequested(), c.scopesGranted(), c.getAcceptedByName(), c.getAcceptedAt(),
                c.getSuspendedBy(), c.getEndedBy(), c.getEndedAt());
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}

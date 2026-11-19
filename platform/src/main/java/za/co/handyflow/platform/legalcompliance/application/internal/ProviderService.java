package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderAssignmentRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderClientRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderReviewRepository;
import za.co.handyflow.platform.legalcompliance.dto.ProviderDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportInfo;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The provider (consultancy) side. Everything here is scoped to the caller's own tenant: the relationship rows belong to the provider, so another provider's id is simply not found.
 *
 * Reading a client's records is the only cross-tenant act, and it happens in exactly two methods (posture, runReport) plus the metrics snapshot taken with a review. All three go through
 * {@link #authorise}, which requires: relationship ACTIVE, the scope granted by the client, and the caller either a provider manager or actively assigned to that client.
 * Each such read is written to the CLIENT's own audit trail (entity PROVIDER_ACCESS) with the consultant's name, so the client can see who looked at what and when.
 * Access is read-only and the reports cannot be exported from here.
 */
@Service
@RequiredArgsConstructor
public class ProviderService {

    static final String ENTITY = "PROVIDER_CLIENT", ACCESS_ENTITY = "PROVIDER_ACCESS";
    private static final SecureRandom RND = new SecureRandom();

    private final ProviderClientRepository clients;
    private final ProviderAssignmentRepository assignments;
    private final ProviderReviewRepository reviews;
    private final ExecutiveService executive;
    private final ReportService reportService;
    private final RegulatoryAuditService trail;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    // ---------------------------------------------------------------- relationship

    @Transactional
    public InviteResult invite(TenantId t, InviteRequest r, UUID uid, String uname) {
        String code = ProviderRules.newCode(RND);
        ProviderClient c = guarded(() -> ProviderClient.invite(t, String.format("CLI-%05d", clients.nextRef()), r.providerName(), r.clientName(), r.scopes(), r.contractStart(), r.contractEnd(),
                r.reviewCycle(), r.notes(), ProviderRules.hash(code), Instant.now()));
        clients.save(c);
        trail.record(t.getValue(), ENTITY, c.getId(), "INVITED", c.getReference() + " " + c.getClientName() + ", asking for " + c.getRequestedScopes().toLowerCase(), uid, uname);
        return new InviteResult(detail(t, c), code);
    }

    @Transactional
    public InviteResult reissue(TenantId t, UUID id, ReissueRequest r, UUID uid, String uname) {
        ProviderClient c = load(t, id);
        String code = ProviderRules.newCode(RND);
        guarded(() -> { c.reissueInvite(ProviderRules.hash(code), r.scopes(), Instant.now()); return null; });
        clients.save(c);
        trail.record(t.getValue(), ENTITY, id, "INVITE_REISSUED", "New invitation code issued, asking for " + c.getRequestedScopes().toLowerCase(), uid, uname);
        return new InviteResult(detail(t, c), code);
    }

    @Transactional
    public ClientDetail update(TenantId t, UUID id, ClientUpdateRequest r, UUID uid, String uname) {
        ProviderClient c = load(t, id);
        guarded(() -> { c.update(r.clientName(), r.contractStart(), r.contractEnd(), r.reviewCycle(), r.notes()); return null; });
        clients.save(c);
        trail.record(t.getValue(), ENTITY, id, "UPDATED", "Details changed", uid, uname);
        return detail(t, c);
    }

    @Transactional
    public ClientDetail suspend(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        ProviderClient c = load(t, id);
        guarded(() -> { c.suspend(AccessSide.PROVIDER, r.reason()); return null; });
        clients.save(c);
        trail.record(t.getValue(), ENTITY, id, "SUSPENDED", "By the provider: " + r.reason().trim(), uid, uname);
        notifyClientSide(c, "SUSPENDED_BY_PROVIDER", "The provider suspended its own access", uid, uname);
        return detail(t, c);
    }

    @Transactional
    public ClientDetail resume(TenantId t, UUID id, UUID uid, String uname) {
        ProviderClient c = load(t, id);
        guarded(() -> { c.resume(AccessSide.PROVIDER); return null; });
        clients.save(c);
        trail.record(t.getValue(), ENTITY, id, "RESUMED", "Access resumed by the provider", uid, uname);
        notifyClientSide(c, "RESUMED_BY_PROVIDER", "The provider resumed its access", uid, uname);
        return detail(t, c);
    }

    /** Withdraws an unaccepted invitation or ends accepted access. Assignments are ended with it; the history is kept. */
    @Transactional
    public ClientDetail end(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        ProviderClient c = load(t, id);
        boolean accepted = c.getClientTenantId() != null;
        guarded(() -> { c.end(AccessSide.PROVIDER, r.reason(), Instant.now()); return null; });
        clients.save(c);
        LocalDate today = today();
        assignments.findForClient(t, id).stream().filter(ProviderAssignment::active).forEach(a -> { a.end(today); assignments.save(a); });
        trail.record(t.getValue(), ENTITY, id, accepted ? "ENDED" : "INVITE_WITHDRAWN", "By the provider: " + r.reason().trim(), uid, uname);
        notifyClientSide(c, "ENDED_BY_PROVIDER", "The provider ended its access", uid, uname);
        return detail(t, c);
    }

    // ---------------------------------------------------------------- reading the portfolio

    @Transactional(readOnly = true)
    public List<ClientResponse> list(TenantId t, UUID uid, boolean manager) {
        List<ProviderClient> all = clients.findAllForTenant(t);
        Map<UUID, List<ProviderAssignment>> team = assignments.findActiveForClients(t, all.stream().map(ProviderClient::getId).toList()).stream()
                .collect(Collectors.groupingBy(ProviderAssignment::getProviderClientId));
        return all.stream()
                .filter(c -> manager || team.getOrDefault(c.getId(), List.of()).stream().anyMatch(a -> a.getUserId().equals(uid)))
                .map(c -> response(c, team.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientDetail get(TenantId t, UUID id, UUID uid, boolean manager) {
        ProviderClient c = load(t, id);
        requireVisible(t, c, uid, manager);
        return detail(t, c);
    }

    @Transactional(readOnly = true)
    public ProviderSummary summary(TenantId t, UUID uid, boolean manager) {
        List<ClientResponse> mine = list(t, uid, manager);
        long active = mine.stream().filter(c -> c.status() == ProviderClientStatus.ACTIVE).count();
        long invited = mine.stream().filter(c -> c.status() == ProviderClientStatus.INVITED && !c.inviteExpired()).count();
        long suspended = mine.stream().filter(c -> c.status() == ProviderClientStatus.SUSPENDED).count();
        long overdue = mine.stream().filter(ClientResponse::reviewOverdue).count();
        long noLead = mine.stream().filter(c -> (c.status() == ProviderClientStatus.ACTIVE || c.status() == ProviderClientStatus.SUSPENDED) && !c.hasLead()).count();
        long contractsEnded = mine.stream().filter(c -> c.contractEnded() && c.status() != ProviderClientStatus.ENDED).count();
        return new ProviderSummary(active, invited, suspended, overdue, noLead, contractsEnded);
    }

    /** Per consultant: how many clients and how many as lead. Managers only (the controller enforces it). */
    @Transactional(readOnly = true)
    public List<WorkloadRow> workload(TenantId t) {
        Map<UUID, ProviderClient> byId = clients.findAllForTenant(t).stream().filter(c -> c.getStatus() != ProviderClientStatus.ENDED).collect(Collectors.toMap(ProviderClient::getId, c -> c));
        Map<UUID, List<ProviderAssignment>> byUser = assignments.findAllActive(t).stream().filter(a -> byId.containsKey(a.getProviderClientId()))
                .collect(Collectors.groupingBy(ProviderAssignment::getUserId, LinkedHashMap::new, Collectors.toList()));
        return byUser.values().stream().map(list -> new WorkloadRow(list.get(0).getUserId(), list.get(0).getUserName(), list.size(),
                        list.stream().filter(a -> a.getRole() == AssignmentRole.LEAD).count(),
                        list.stream().map(a -> byId.get(a.getProviderClientId()).getClientName()).sorted().toList()))
                .sorted(Comparator.comparingLong(WorkloadRow::clients).reversed().thenComparing(WorkloadRow::userName)).toList();
    }

    // ---------------------------------------------------------------- team

    @Transactional
    public ClientDetail assign(TenantId t, UUID id, AssignRequest r, UUID uid, String uname) {
        ProviderClient c = load(t, id);
        if (c.getStatus() == ProviderClientStatus.ENDED) throw new HandyFlowException("Consultants cannot be assigned to a client whose relationship has ended", HttpStatus.CONFLICT, "INVALID_STATE");
        List<ProviderAssignment> current = assignments.findForClient(t, id).stream().filter(ProviderAssignment::active).toList();
        if (current.stream().anyMatch(a -> a.getUserId().equals(r.userId()))) throw new HandyFlowException(r.userName() + " is already assigned to this client", HttpStatus.CONFLICT, "INVALID_STATE");
        if (r.role() == AssignmentRole.LEAD && current.stream().anyMatch(a -> a.getRole() == AssignmentRole.LEAD))
            throw new HandyFlowException("This client already has a lead. End that assignment first or assign this person as reviewer or support", HttpStatus.CONFLICT, "INVALID_STATE");
        ProviderAssignment a = guarded(() -> ProviderAssignment.assign(t, id, r.userId(), r.userName(), r.role(), uname, today()));
        assignments.save(a);
        trail.record(t.getValue(), ENTITY, id, "CONSULTANT_ASSIGNED", a.getUserName() + " as " + a.getRole().name().toLowerCase(), uid, uname);
        return detail(t, c);
    }

    @Transactional
    public ClientDetail endAssignment(TenantId t, UUID id, UUID assignmentId, UUID uid, String uname) {
        ProviderClient c = load(t, id);
        ProviderAssignment a = assignments.findForTenant(t, assignmentId).filter(x -> x.getProviderClientId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId.toString()));
        guarded(() -> { a.end(today()); return null; });
        assignments.save(a);
        trail.record(t.getValue(), ENTITY, id, "CONSULTANT_UNASSIGNED", a.getUserName() + " (" + a.getRole().name().toLowerCase() + ")", uid, uname);
        return detail(t, c);
    }

    // ---------------------------------------------------------------- reviews

    /**
     * Periodic client review by the person recording it (never on someone else's behalf). When the relationship is ACTIVE and the client granted SUMMARY, the raw counts seen are kept on the review
     * (and the read is logged to the client's trail); otherwise the review is recorded without them.
     */
    @Transactional
    public ClientDetail recordReview(TenantId t, UUID id, ReviewRequest r, UUID uid, String uname, boolean manager) {
        ProviderClient c = load(t, id);
        requireVisible(t, c, uid, manager);
        Map<String, Long> metrics = null;
        if (c.getStatus() == ProviderClientStatus.ACTIVE && c.scopesGranted().contains(AccessScope.SUMMARY)) {
            metrics = executive.collect(TenantId.of(c.getClientTenantId()));
            clientTrail(c, "REVIEW_SUMMARY_READ", c.getProviderName() + " read the compliance summary for a periodic review", uid, uname);
        }
        LocalDate today = today();
        final Map<String, Long> seen = metrics;
        ProviderReview review = guarded(() -> {
            c.scheduleReview(r.reviewDate());
            return ProviderReview.record(t, id, r.reviewDate(), uid, uname, r.outcome(), r.notes(), seen, c.getNextReviewDue(), today);
        });
        clients.save(c);
        reviews.save(review);
        trail.record(t.getValue(), ENTITY, id, "REVIEWED", r.outcome().name() + ", next review " + c.getNextReviewDue(), uid, uname);
        return detail(t, c);
    }

    // ---------------------------------------------------------------- reading a client's workspace (cross-tenant, read-only)

    @Transactional(readOnly = true)
    public ClientPosture posture(TenantId t, UUID id, UUID uid, String uname, boolean manager) {
        ProviderClient c = load(t, id);
        authorise(t, c, AccessScope.SUMMARY, uid, manager);
        var overview = executive.overview(TenantId.of(c.getClientTenantId()));
        clientTrail(c, "SUMMARY_VIEWED", c.getProviderName() + " viewed the compliance summary", uid, uname);
        return new ClientPosture(c.getId(), c.getClientName(), overview);
    }

    @Transactional(readOnly = true)
    public List<ReportInfo> reportCatalogue(TenantId t, UUID id, UUID uid, boolean manager) {
        ProviderClient c = load(t, id);
        authorise(t, c, AccessScope.REPORTS, uid, manager);
        return reportService.catalogue();
    }

    @Transactional(readOnly = true)
    public ReportResult runReport(TenantId t, UUID id, String key, String asOf, UUID uid, String uname, boolean manager) {
        ProviderClient c = load(t, id);
        authorise(t, c, AccessScope.REPORTS, uid, manager);
        ReportResult result = reportService.run(TenantId.of(c.getClientTenantId()), key, asOf, uname);
        clientTrail(c, "REPORT_RUN", c.getProviderName() + " ran the report \"" + result.title() + "\" (" + result.rowCount() + " rows)", uid, uname);
        return result;
    }

    // ---------------------------------------------------------------- internals

    /** The single gate for cross-tenant reads. Denials are 403 and say only what the consultant needs to know. */
    private void authorise(TenantId t, ProviderClient c, AccessScope scope, UUID uid, boolean manager) {
        boolean assigned = manager || assignments.findActive(t, c.getId(), uid).isPresent();
        String why = ProviderRules.denial(c.getStatus(), c.scopesGranted(), scope, manager, assigned);
        if (why != null) throw new HandyFlowException(why, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        if (c.getClientTenantId() == null) throw new HandyFlowException("Access to this client is not currently active", HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private void requireVisible(TenantId t, ProviderClient c, UUID uid, boolean manager) {
        if (!manager && assignments.findActive(t, c.getId(), uid).isEmpty()) throw new ResourceNotFoundException("Client", c.getId().toString());
    }

    /** Written to the client's own trail. The actor is the consultant; the entity is the relationship, so the client can list it. */
    private void clientTrail(ProviderClient c, String event, String detail, UUID uid, String uname) {
        trail.record(c.getClientTenantId(), ACCESS_ENTITY, c.getId(), event, detail, uid, uname);
    }

    private void notifyClientSide(ProviderClient c, String event, String detail, UUID uid, String uname) {
        if (c.getClientTenantId() != null) clientTrail(c, event, detail, uid, uname);
    }

    private ProviderClient load(TenantId t, UUID id) {
        return clients.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Client", id.toString()));
    }

    private ClientDetail detail(TenantId t, ProviderClient c) {
        List<ProviderAssignment> all = assignments.findForClient(t, c.getId());
        List<ProviderAssignment> active = all.stream().filter(ProviderAssignment::active).toList();
        return new ClientDetail(response(c, active), all.stream().map(AssignmentResponse::of).toList(), reviews.findForClient(t, c.getId()).stream().map(ReviewResponse::of).toList());
    }

    static ClientResponse response(ProviderClient c, List<ProviderAssignment> active) {
        LocalDate today = today();
        Instant now = Instant.now();
        boolean invited = c.getStatus() == ProviderClientStatus.INVITED;
        return new ClientResponse(c.getId(), c.getReference(), c.getClientName(), c.getProviderName(), c.getStatus(), c.scopesRequested(), c.scopesGranted(),
                invited && ProviderRules.inviteExpired(c.getInviteExpiresAt(), now), invited ? c.getInviteExpiresAt() : null, c.getAcceptedAt(), c.getAcceptedByName(),
                c.getSuspendedBy(), c.getStatusReason(), c.getContractStart(), c.getContractEnd(), c.getContractEnd() != null && today.isAfter(c.getContractEnd()),
                c.getReviewCycle(), c.getNextReviewDue(), ProviderRules.reviewOverdue(c.getStatus(), c.getNextReviewDue(), today),
                active.stream().map(AssignmentResponse::of).toList(), active.stream().anyMatch(a -> a.getRole() == AssignmentRole.LEAD), c.getNotes(), c.getEndedAt(), c.getEndedBy());
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}

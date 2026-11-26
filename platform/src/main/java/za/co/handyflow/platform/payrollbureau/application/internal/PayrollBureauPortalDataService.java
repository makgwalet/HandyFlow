package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClient;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;
import za.co.handyflow.platform.payrollbureau.domain.rules.RunLifecycleRules;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.payrollbureau.domain.model.PayDeadline;
import za.co.handyflow.platform.payrollbureau.domain.model.PayFeeNote;
import za.co.handyflow.platform.payrollbureau.domain.model.PayPortalAccessGrant;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Direct structural mirror of accountant.AccountantPortalDataService's
 * pattern: every method starts with requireAccess(), tenant resolved
 * from the GRANT, never TenantContext (a portal user isn't tied to one
 * tenant the way staff are — see PortalJwtFilter's own Javadoc).
 * <p>
 * Scoped to fee notes and deadlines only for this first pass — the two
 * things a payroll bureau's client actually needs to see (their
 * invoices, their compliance status), matching the two most directly
 * analogous features already proven in the accountant portal. Employee-
 * level payslip access is NOT this — see PayPortalAccessGrant's own
 * Javadoc SCOPE NOTE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollBureauPortalDataService {

    private final PayPortalAccessGrantRepository grantRepo;
    private final PayClientRepository clientRepo;
    private final PayFeeNoteRepository feeNoteRepo;
    private final PayDeadlineRepository deadlineRepo;
    private final za.co.handyflow.platform.payrollbureau.domain.repository.PayRunRepository payRunRepo;
    private final za.co.handyflow.platform.payrollbureau.domain.repository.PayslipRepository payslipRepo;
    private final PayrollBureauService bureauService;

    @Transactional(readOnly = true)
    public List<PortalClientSummaryResponse> getMyClients(UUID portalUserId) {
        return grantRepo.findActiveGrantsForUser(portalUserId).stream()
                .map(g -> clientRepo.findById(g.getPayClientId())
                        .map(c -> new PortalClientSummaryResponse(c.getId(), c.getTradingName()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PayFeeNoteResponse> getMyFeeNotes(UUID portalUserId, UUID clientId, Pageable pageable) {
        requireAccess(portalUserId, clientId);
        return feeNoteRepo.findByClient(clientId, pageable).map(this::toFeeNoteResponse);
    }

    @Transactional(readOnly = true)
    public List<PayDeadlineResponse> getMyDeadlines(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        LocalDate today = LocalDate.now();
        return deadlineRepo.findByClient(clientId).stream()
                .map(d -> new PayDeadlineResponse(d.getId(), d.getDeadlineType(), d.getPeriodYear(),
                        d.getPeriodMonth(), d.getAdjustedDueDate(), d.getStatus(), d.getFiledDate(),
                        ChronoUnit.DAYS.between(today, d.getAdjustedDueDate())))
                .toList();
    }

    // ── Pay runs: the client's approval and payslips (phase 5) ──────────────────

    @Transactional(readOnly = true)
    public List<PortalPayRunDtos.PortalPayRun> getMyPayRuns(UUID portalUserId, UUID clientId) {
        PayPortalAccessGrant grant = requireAccess(portalUserId, clientId);
        return payRunRepo.findForPortal(grant.getTenantId(), clientId).stream().map(this::toPortalRun).toList();
    }

    @Transactional(readOnly = true)
    public PortalPayRunDtos.PortalPayRunDetail getMyPayRun(UUID portalUserId, UUID clientId, UUID payRunId) {
        PayPortalAccessGrant grant = requireAccess(portalUserId, clientId);
        PayRun run = loadVisibleRun(grant, clientId, payRunId);
        TenantId tenant = TenantId.of(grant.getTenantId());
        return new PortalPayRunDtos.PortalPayRunDetail(toPortalRun(run), bureauService.getPayslips(tenant, payRunId),
                bureauService.reviewPayRun(tenant, payRunId));
    }

    @Transactional
    public PortalPayRunDtos.PortalPayRun approveMyPayRun(UUID portalUserId, UUID clientId, UUID payRunId) {
        PayPortalAccessGrant grant = requireAccess(portalUserId, clientId);
        PayRun run = loadVisibleRun(grant, clientId, payRunId);
        requireTransition(run, RunLifecycleRules.CLIENT_APPROVED);
        run.clientApprove(portalUserId);
        payRunRepo.save(run);
        log.info("Pay run approved by client run={} portalUser={}", run.getPayRunNumber(), portalUserId);
        return toPortalRun(run);
    }

    @Transactional
    public PortalPayRunDtos.PortalPayRun declineMyPayRun(UUID portalUserId, UUID clientId, UUID payRunId, String reason) {
        PayPortalAccessGrant grant = requireAccess(portalUserId, clientId);
        PayRun run = loadVisibleRun(grant, clientId, payRunId);
        String problem = RunLifecycleRules.declineProblem(reason);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        requireTransition(run, RunLifecycleRules.DRAFT);
        bureauService.deletePayslipsOfRun(TenantId.of(grant.getTenantId()), payRunId);
        run.clientDecline(reason);
        payRunRepo.save(run);
        log.warn("Pay run declined by client run={} portalUser={} reason={}", run.getPayRunNumber(), portalUserId, reason);
        return toPortalRun(run);
    }

    private PayRun loadVisibleRun(PayPortalAccessGrant grant, UUID clientId, UUID payRunId) {
        PayRun run = payRunRepo.findByTenantAndId(grant.getTenantId(), payRunId)
                .filter(r -> r.getPayClientId().equals(clientId))
                .orElseThrow(() -> new HandyFlowException("Pay run not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!List.of("AWAITING_CLIENT", "CLIENT_APPROVED", "FINALISED", "REVERSED").contains(run.getStatus())) {
            throw new HandyFlowException("Pay run not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return run;
    }

    private void requireTransition(PayRun run, String to) {
        String problem = RunLifecycleRules.transitionProblem(run.getStatus(), to);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_STATUS");
    }

    private PortalPayRunDtos.PortalPayRun toPortalRun(PayRun r) {
        return new PortalPayRunDtos.PortalPayRun(r.getId(), r.getPayRunNumber(), r.getPeriodStart(), r.getPeriodEnd(), r.getPayDate(),
                r.getStatus(), r.getTotalGross(), r.getTotalPaye(), r.getTotalUif(), r.getTotalSdl(), r.getTotalNet(),
                r.getEmployeeCount(), r.getSentToClientAt(), r.getClientApprovedAt(), r.getClientDeclinedAt(), r.getClientDeclineReason());
    }

    private PayPortalAccessGrant requireAccess(UUID portalUserId, UUID clientId) {
        return grantRepo.findActiveGrant(portalUserId, clientId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this client", HttpStatus.FORBIDDEN, "NO_ACCESS"));
    }

    private PayFeeNoteResponse toFeeNoteResponse(PayFeeNote f) {
        return new PayFeeNoteResponse(f.getId(), f.getInvoiceNumber(), f.getInvoiceDate(), f.getDueDate(),
                f.getSubtotal(), f.getVatAmount(), f.getTotal(), f.getAmountPaid(), f.balance(),
                f.getStatus(), f.getSentAt(), f.getPaidAt());
    }
}
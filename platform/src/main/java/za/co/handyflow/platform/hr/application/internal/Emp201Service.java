package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.Emp201Rules;
import za.co.handyflow.platform.hr.domain.model.HrEmp201;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.model.HrPayRunEvent;
import za.co.handyflow.platform.hr.domain.model.PayRunWorkflowRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmp201Repository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunEventRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository;
import za.co.handyflow.platform.hr.dto.Emp201ActionDtos.PayRequest;
import za.co.handyflow.platform.hr.dto.Emp201ActionDtos.SubmitRequest;
import za.co.handyflow.platform.hr.dto.Emp201Response;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Moving an EMP201 on once it has been filed with SARS and again once it has been paid. Both steps are written to the history of the
 * pay run behind it. Nothing is sent to SARS from here; the filing and the payment are made on eFiling and recorded afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Emp201Service {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final HrEmp201Repository repo;
    private final HrPayRunRepository payRunRepo;
    private final HrPayRunEventRepository eventRepo;

    @Transactional
    public Emp201Response submit(TenantId tenantId, UUID id, SubmitRequest req, UUID actor) {
        requireActor(actor);
        HrEmp201 e = find(tenantId, id);
        HrPayRun run = run(tenantId, e);
        String problem = Emp201Rules.submitProblem(e.getStatus(), run == null || PayRunWorkflowRules.isIssued(run.getStatus()), req == null ? null : req.paymentRef());
        if (problem != null) throw new IllegalStateException(problem);
        e.markSubmitted(req.paymentRef(), actor);
        repo.save(e);
        event(tenantId, run, "EMP201_FILED", actor, "Reference " + e.getPaymentRef() + ", R" + e.getTotalPayable().toPlainString());
        return view(e, run == null || PayRunWorkflowRules.isIssued(run.getStatus()));
    }

    @Transactional
    public Emp201Response pay(TenantId tenantId, UUID id, PayRequest req, UUID actor) {
        requireActor(actor);
        HrEmp201 e = find(tenantId, id);
        HrPayRun run = run(tenantId, e);
        String problem = Emp201Rules.payProblem(e.getStatus(), req == null ? null : req.paidOn(), req == null ? null : req.amount(),
                e.getTotalPayable(), e.getPeriodEnd(), LocalDate.now(ZONE));
        if (problem != null) throw new IllegalStateException(problem);
        e.markPaid(req.paidOn(), req.amount(), actor);
        repo.save(e);
        event(tenantId, run, "EMP201_PAID", actor, "R" + e.getPaidAmount().toPlainString() + " on " + e.getPaidOn()
                + (Emp201Rules.late(e.getPaidOn(), e.getDueDate()) ? " (after the due date " + e.getDueDate() + ")" : ""));
        return view(e, run == null || PayRunWorkflowRules.isIssued(run.getStatus()));
    }

    private Emp201Response view(HrEmp201 e, boolean runIssued) {
        LocalDate today = LocalDate.now(ZONE);
        return new Emp201Response(e.getId(), e.getPayRunId(), e.getPeriodStart(), e.getPeriodEnd(), e.getDueDate(), e.getTotalPaye(), e.getTotalUif(),
                e.getTotalSdl(), e.getTotalPayable(), e.getStatus(), e.getSubmittedAt(), e.getCreatedAt(), e.getPaymentRef(), e.getPaidOn(),
                e.getPaidAmount(), Emp201Rules.overdue(e.getStatus(), e.getDueDate(), today), Emp201Rules.late(e.getPaidOn(), e.getDueDate()), runIssued,
                e.getTotalEti(), e.getEtiCarried());
    }

    private HrEmp201 find(TenantId tenantId, UUID id) {
        return repo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Emp201", id.toString()));
    }

    private HrPayRun run(TenantId tenantId, HrEmp201 e) {
        return e.getPayRunId() == null ? null : payRunRepo.findByTenantAndId(tenantId, e.getPayRunId()).orElse(null);
    }

    private void event(TenantId tenantId, HrPayRun run, String action, UUID actor, String note) {
        if (run == null) return;
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), run.getId(), action, run.getStatus(), run.getStatus(), actor, note));
        log.info("EMP201 of pay run {}: {} by {}", run.getPayRunNumber(), action, actor);
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
}

package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClient;
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
package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;
import za.co.handyflow.platform.legalcompliance.domain.repository.LitigationMatterRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.RegulatoryObligationRepository;
import za.co.handyflow.platform.legalcompliance.dto.CalendarEntryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates three independent date-bearing sources into one chronological
 * calendar view: this module's own regulatory obligation review dates and
 * litigation matter key dates, plus contract renewal/expiry dates read
 * (never written) via ContractingFacade — the concrete cross-module
 * integration point the "Facade over contracting" decision exists for.
 * <p>
 * Deliberately does not persist anything of its own — this is a read-time
 * projection, recomputed on every call. If that becomes a performance
 * concern at scale, the fix is caching this result, not turning it into a
 * new writable aggregate that would then need to be kept in sync with the
 * three sources it summarizes.
 */
@Service
@RequiredArgsConstructor
public class LegalComplianceCalendarService {

    private final RegulatoryObligationRepository obligationRepository;
    private final LitigationMatterRepository matterRepository;
    private final ContractingFacade contractingFacade;

    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> upcoming(TenantId tenantId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate to = today.plusDays(days);
        List<CalendarEntryResponse> entries = new ArrayList<>();

        List<RegulatoryObligation> obligations = obligationRepository.findDueWithin(tenantId, today, to);
        for (RegulatoryObligation o : obligations) {
            entries.add(new CalendarEntryResponse(o.getReviewDate(), "OBLIGATION", o.getId(),
                    o.getTitle(), "Review due — " + o.getCategory() + " (" + o.getStatus() + ")"));
        }

        List<LitigationMatter> matters = matterRepository.findWithKeyDateWithin(tenantId, today, to);
        for (LitigationMatter m : matters) {
            entries.add(new CalendarEntryResponse(m.getNextKeyDate(), "LITIGATION", m.getId(),
                    m.getMatterNumber() + " — " + m.getTitle(),
                    "Key date — " + m.getMatterType() + " vs. " + m.getOpposingParty()));
        }

        List<ContractSummary> contracts = contractingFacade.listExpiringWithin(tenantId, days);
        for (ContractSummary c : contracts) {
            if (c.endDate() == null) continue;
            entries.add(new CalendarEntryResponse(c.endDate(), "CONTRACT_RENEWAL", c.id(),
                    c.contractNumber() + " — " + c.title(),
                    (c.autoRenew() ? "Auto-renews" : "Expires") + " — " + c.contractType()));
        }

        entries.sort(Comparator.comparing(CalendarEntryResponse::date));
        return entries;
    }
}

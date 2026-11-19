package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.GrcDtos.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.RiskRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/** Where the Obligation → Risk → Control chain has gaps. Read-only projection computed on demand. */
@Service
@RequiredArgsConstructor
public class CoverageService {

    private final RegulatoryObligationRepository obligations;
    private final RiskRepository risks;
    private final RiskAssessmentRepository assessments;
    private final ControlRepository controls;
    private final GrcLinkRepository links;

    @Transactional(readOnly = true)
    public CoverageResponse coverage(TenantId tenantId) {
        Map<UUID, Control> ctl = controls.search(tenantId, null, true).stream().collect(Collectors.toMap(Control::getId, c -> c));
        List<GrcLink> ctlObl = links.findAllOfType(tenantId, GrcLinkType.CONTROL_OBLIGATION);
        List<GrcLink> riskObl = links.findAllOfType(tenantId, GrcLinkType.RISK_OBLIGATION);
        List<GrcLink> riskCtl = links.findAllOfType(tenantId, GrcLinkType.RISK_CONTROL);

        List<ObligationCoverage> rows = new ArrayList<>();
        int uncovered = 0, untested = 0, effective = 0;
        for (RegulatoryObligation o : obligations.findAllActive(tenantId, null, Pageable.unpaged()).getContent()) {
            int active = 0, eff = 0;
            for (GrcLink l : ctlObl) {
                if (!l.getTargetId().equals(o.getId())) continue;
                Control c = ctl.get(l.getSourceId());
                if (c == null || !c.isActive()) continue;
                active++;
                if (ControlTestRules.currentlyEffective(c.getOperatingEffectiveness(), c.getNextTestDue(), java.time.LocalDate.now())) eff++;   // a lapsed pass is not effective
            }
            String state = CoverageRules.obligationState(active, eff);
            switch (state) { case "UNCOVERED" -> uncovered++; case "COVERED_UNTESTED" -> untested++; default -> effective++; }
            rows.add(new ObligationCoverage(o.getId(), o.getTitle(), o.getRegulationReference(), o.getCategory().name(), state, active, eff,
                    (int) riskObl.stream().filter(l -> l.getTargetId().equals(o.getId())).count()));
        }
        rows.sort(Comparator.comparing((ObligationCoverage r) -> "UNCOVERED".equals(r.state()) ? 0 : 1).thenComparing(ObligationCoverage::title, String.CASE_INSENSITIVE_ORDER));

        Map<UUID, RiskAssessment> current = assessments.findAllCurrent(tenantId).stream().collect(Collectors.toMap(RiskAssessment::getRiskId, a -> a, (x, y) -> x));
        List<RiskCoverage> unmitigated = new ArrayList<>();
        for (Risk r : risks.search(tenantId, null, null, false)) {
            int active = (int) riskCtl.stream().filter(l -> l.getSourceId().equals(r.getId())).map(l -> ctl.get(l.getTargetId())).filter(c -> c != null && c.isActive()).count();
            if (active == 0) {
                RiskAssessment a = current.get(r.getId());
                unmitigated.add(new RiskCoverage(r.getId(), r.getReference(), r.getTitle(), a == null ? null : a.getResidualRating(), 0));
            }
        }
        Set<UUID> linkedControls = new HashSet<>();
        ctlObl.forEach(l -> linkedControls.add(l.getSourceId()));
        riskCtl.forEach(l -> linkedControls.add(l.getTargetId()));
        int draftUnlinked = (int) ctl.values().stream().filter(c -> c.getStatus() == ControlStatus.DRAFT && !linkedControls.contains(c.getId())).count();

        return new CoverageResponse(rows.size(), uncovered, untested, effective, unmitigated.size(), draftUnlinked, rows, unmitigated);
    }
}

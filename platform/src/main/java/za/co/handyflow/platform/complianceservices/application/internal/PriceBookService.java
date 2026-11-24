package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The practice's price book. A version is written as a draft, approved by a second person and then never changed.
 * It prices only services that exist in the catalogue and holds no government fees: those are added to a quote at cost.
 */
@Service
@RequiredArgsConstructor
public class PriceBookService {

    private final CompliancePriceBookVersionRepository versions;
    private final CompliancePriceBookLineRepository lines;
    private final CompliancePriceBookRateRepository rates;
    private final ComplianceServiceDefinitionRepository services;
    private final ComplianceServiceVersionRepository serviceVersions;

    @Transactional(readOnly = true)
    public List<VersionView> list(TenantId tenantId, boolean admin) {
        return views(tenantId, admin);
    }

    /** Starts the first draft, or the next one as a copy of the latest version. */
    @Transactional
    public List<VersionView> newVersion(TenantId tenantId, UUID by, boolean admin) {
        List<CompliancePriceBookVersion> all = versions.findAllForTenant(tenantId);
        if (all.stream().anyMatch(v -> !v.isApproved())) {
            throw new HandyFlowException("There is already a draft or a version in review", HttpStatus.CONFLICT, "PRICE_BOOK_DRAFT_EXISTS");
        }
        CompliancePriceBookVersion last = all.stream().max(Comparator.comparingInt(CompliancePriceBookVersion::getVersionNumber)).orElse(null);
        CompliancePriceBookVersion next = versions.save(CompliancePriceBookVersion.draft(tenantId, last == null ? 1 : last.getVersionNumber() + 1, by));
        if (last != null) {
            int i = 0;
            for (CompliancePriceBookLine l : lines.findForVersions(tenantId, List.of(last.getId()))) {
                lines.save(CompliancePriceBookLine.of(tenantId, next.getId(), new CompliancePriceBookLine.Input(l.getServiceCode(), l.getPricingModel(),
                        l.getBaseFee(), l.getUnitRate(), l.getHourlyGrade(), l.getMinFee(), l.getUrgentSurchargePct(), l.getTaxTreatment(), l.getNotes()), i++));
            }
            i = 0;
            for (CompliancePriceBookRate r : rates.findForVersions(tenantId, List.of(last.getId()))) {
                rates.save(CompliancePriceBookRate.of(tenantId, next.getId(), r.getGrade(), r.getHourlyRate(), r.getCostRate(), i++));
            }
        }
        return views(tenantId, admin);
    }

    @Transactional
    public List<VersionView> saveDraft(TenantId tenantId, UUID versionId, SaveRequest r, UUID by, boolean admin) {
        CompliancePriceBookVersion v = find(tenantId, versionId);
        if (!v.isDraft()) {
            throw new HandyFlowException("Only a draft can be edited. Start a new version to change an approved one.", HttpStatus.CONFLICT, "PRICE_BOOK_LOCKED");
        }
        try {
            v.edit(r.label(), r.changeSummary());
            List<CompliancePriceBookLine> newLines = new ArrayList<>();
            List<CompliancePriceBookRate> newRates = new ArrayList<>();
            // an editor who is not an administrator never sees cost rates, so what is on file is kept as it is
            Map<String, BigDecimal> keptCost = admin ? Map.of() : rates.findForVersions(tenantId, List.of(v.getId())).stream()
                    .filter(x -> x.getCostRate() != null).collect(Collectors.toMap(CompliancePriceBookRate::getGrade, CompliancePriceBookRate::getCostRate, (a, b) -> a));
            int i = 0;
            for (RateInput in : r.rates() == null ? List.<RateInput>of() : r.rates()) {
                String grade = PricingRules.grade(in.grade());
                BigDecimal cost = admin ? in.costRate() : keptCost.get(grade);
                newRates.add(CompliancePriceBookRate.of(tenantId, v.getId(), grade, in.hourlyRate(), cost, i++));
            }
            Set<String> grades = new HashSet<>();
            for (CompliancePriceBookRate x : newRates) {
                if (!grades.add(x.getGrade())) throw new IllegalArgumentException("Grade " + x.getGrade() + " is listed twice");
            }
            Set<String> known = services.findAllForTenant(tenantId).stream().map(ComplianceServiceDefinition::getCode).collect(Collectors.toSet());
            Set<String> codes = new HashSet<>();
            i = 0;
            for (LineInput in : r.lines() == null ? List.<LineInput>of() : r.lines()) {
                CompliancePriceBookLine l = CompliancePriceBookLine.of(tenantId, v.getId(), new CompliancePriceBookLine.Input(in.serviceCode(), in.pricingModel(),
                        in.baseFee(), in.unitRate(), in.hourlyGrade(), in.minFee(), in.urgentSurchargePct(), in.taxTreatment(), in.notes()), i++);
                if (!known.contains(l.getServiceCode())) throw new IllegalArgumentException("Service " + l.getServiceCode() + " is not in the service catalogue");
                if (!codes.add(l.getServiceCode())) throw new IllegalArgumentException("Service " + l.getServiceCode() + " is priced twice");
                if (l.getHourlyGrade() != null && !grades.contains(l.getHourlyGrade())) {
                    throw new IllegalArgumentException("Service " + l.getServiceCode() + " uses grade " + l.getHourlyGrade() + ", which has no hourly rate");
                }
                newLines.add(l);
            }
            lines.deleteForVersion(v.getId());
            rates.deleteForVersion(v.getId());
            lines.saveAll(newLines);
            rates.saveAll(newRates);
        } catch (IllegalArgumentException e) {
            throw bad(e);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PRICE_BOOK_LOCKED");
        }
        versions.save(v);
        return views(tenantId, admin);
    }

    @Transactional
    public List<VersionView> submit(TenantId tenantId, UUID versionId, UUID by, boolean admin) {
        CompliancePriceBookVersion v = find(tenantId, versionId);
        List<CompliancePriceBookLine> ls = lines.findForVersions(tenantId, List.of(v.getId()));
        int incomplete = (int) ls.stream().filter(l -> !l.gaps().isEmpty()).count();
        transition(() -> v.submit(by, ls.size(), incomplete));
        versions.save(v);
        return views(tenantId, admin);
    }

    @Transactional
    public List<VersionView> approve(TenantId tenantId, UUID versionId, ApproveRequest r, UUID by, boolean admin) {
        CompliancePriceBookVersion v = find(tenantId, versionId);
        LocalDate from = r == null ? null : r.effectiveFrom();
        transition(() -> v.approve(by, from, r == null ? null : r.exceptionReason(), r == null ? null : r.note()));
        // the version before it stops the day before this one starts
        versions.findAllForTenant(tenantId).stream()
                .filter(p -> p.isApproved() && p.getVersionNumber() < v.getVersionNumber())
                .max(Comparator.comparingInt(CompliancePriceBookVersion::getVersionNumber))
                .ifPresent(prev -> {
                    if (prev.getEffectiveTo() == null || !prev.getEffectiveTo().isBefore(from)) {
                        transition(() -> prev.endOn(ObligationRules.endOfPrevious(prev.getEffectiveFrom(), from)));
                        versions.save(prev);
                    }
                });
        versions.save(v);
        return views(tenantId, admin);
    }

    @Transactional
    public List<VersionView> reject(TenantId tenantId, UUID versionId, String reason, UUID by, boolean admin) {
        CompliancePriceBookVersion v = find(tenantId, versionId);
        transition(() -> v.reject(by, reason));
        versions.save(v);
        return views(tenantId, admin);
    }

    /** Stops quoting from the version now in force from a date. Quotes already given keep their version. */
    @Transactional
    public List<VersionView> retire(TenantId tenantId, LocalDate on, UUID by, boolean admin) {
        LocalDate date = on == null ? LocalDate.now() : on;
        CompliancePriceBookVersion open = versions.findAllForTenant(tenantId).stream()
                .filter(v -> v.isApproved() && v.getEffectiveTo() == null)
                .max(Comparator.comparingInt(CompliancePriceBookVersion::getVersionNumber))
                .orElseThrow(() -> new HandyFlowException("No approved price book is open-ended", HttpStatus.CONFLICT, "PRICE_BOOK_NOT_IN_FORCE"));
        transition(() -> open.endOn(date));
        versions.save(open);
        return views(tenantId, admin);
    }

    // ---- helpers ----

    private List<VersionView> views(TenantId tenantId, boolean admin) {
        List<CompliancePriceBookVersion> vs = versions.findAllForTenant(tenantId);
        if (vs.isEmpty()) return List.of();
        List<UUID> ids = vs.stream().map(CompliancePriceBookVersion::getId).toList();
        Map<UUID, List<CompliancePriceBookLine>> ls = lines.findForVersions(tenantId, ids).stream()
                .collect(Collectors.groupingBy(CompliancePriceBookLine::getVersionId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<CompliancePriceBookRate>> rs = rates.findForVersions(tenantId, ids).stream()
                .collect(Collectors.groupingBy(CompliancePriceBookRate::getVersionId, LinkedHashMap::new, Collectors.toList()));
        Map<String, String> names = serviceNames(tenantId);
        Integer inForce = ObligationRules.effectiveOn(vs.stream().map(CompliancePriceBookVersion::span).toList(), LocalDate.now());
        List<VersionView> out = new ArrayList<>();
        for (CompliancePriceBookVersion v : vs) {
            List<LineView> lineViews = new ArrayList<>();
            for (CompliancePriceBookLine l : ls.getOrDefault(v.getId(), List.of())) lineViews.add(lineView(l, names.get(l.getServiceCode())));
            List<RateView> rateViews = new ArrayList<>();
            for (CompliancePriceBookRate x : rs.getOrDefault(v.getId(), List.of())) rateViews.add(new RateView(x.getGrade(), x.getHourlyRate(), admin ? x.getCostRate() : null));
            boolean current = inForce != null && inForce == v.getVersionNumber();
            out.add(new VersionView(v.getId(), v.getVersionNumber(), v.getReviewStatus(), v.getCurrency(), v.getLabel(), v.getChangeSummary(),
                    v.getAuthoredBy(), v.getSubmittedBy(), v.getSubmittedAt(), v.getReviewedBy(), v.getReviewedAt(), v.getReviewNote(), v.getReviewExceptionReason(),
                    v.getEffectiveFrom(), v.getEffectiveTo(), current, lineViews, rateViews));
        }
        return out;
    }

    private static LineView lineView(CompliancePriceBookLine l, String serviceName) {
        return new LineView(l.getServiceCode(), serviceName, l.getPricingModel(), l.getBaseFee(), l.getUnitRate(), l.getHourlyGrade(), l.getMinFee(),
                l.getUrgentSurchargePct(), l.getTaxTreatment(), l.getNotes(), l.gaps());
    }

    /** The name a service is known by now: its latest version's name. */
    private Map<String, String> serviceNames(TenantId tenantId) {
        Map<UUID, String> byId = new HashMap<>();
        Map<UUID, Integer> latest = new HashMap<>();
        for (ComplianceServiceVersion sv : serviceVersions.findAllForTenant(tenantId)) {
            Integer n = latest.get(sv.getServiceId());
            if (n == null || sv.getVersionNumber() > n) {
                latest.put(sv.getServiceId(), sv.getVersionNumber());
                byId.put(sv.getServiceId(), sv.getName());
            }
        }
        Map<String, String> out = new HashMap<>();
        for (ComplianceServiceDefinition s : services.findAllForTenant(tenantId)) out.put(s.getCode(), byId.get(s.getId()));
        return out;
    }

    private CompliancePriceBookVersion find(TenantId tenantId, UUID id) {
        return versions.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("CompliancePriceBookVersion", id.toString()));
    }

    private static void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PRICE_BOOK_WORKFLOW");
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
    }

    private static HandyFlowException bad(IllegalArgumentException e) {
        return new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "PRICE_BOOK_INVALID");
    }
}

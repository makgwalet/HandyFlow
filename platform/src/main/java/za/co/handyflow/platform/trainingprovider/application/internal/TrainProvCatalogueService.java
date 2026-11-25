package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.CatalogueRules;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.CoverageView;
import za.co.handyflow.platform.trainingprovider.dto.CatalogueDtos.*;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Qualifications (the official reference) and programmes (what the provider
 * offers), both versioned. A published or approved version is frozen so a
 * learner's record keeps the version they studied under. An accredited
 * programme can only be offered at a site that a current approval covers.
 * Nothing is deleted; things are retired or withdrawn with a reason.
 */
@Service
@RequiredArgsConstructor
public class TrainProvCatalogueService {

    private final TrainProvQualificationRepository qualifications;
    private final TrainProvQualificationVersionRepository qualVersions;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvProgrammeModuleRepository modules;
    private final TrainProvProgrammeOfferingRepository offerings;
    private final TrainProvDeliverySiteRepository sites;
    private final TrainProvAuthorityRepository authorities;
    private final TrainProvCourseRepository courses;
    private final TrainProvAccreditationService accreditation;

    // ---- qualifications ----------------------------------------------------

    public List<QualificationView> qualifications(TenantId t, String status) {
        return qualifications.findAllForTenant(t, status).stream().<QualificationView>map(q -> qualView(t, q)).toList();
    }

    public QualificationDetail qualification(TenantId t, UUID id) {
        TrainProvQualification q = qualification(t, id, true);
        List<QualificationVersionView> vs = qualVersions.findForQualification(t, id).stream()
                .<QualificationVersionView>map(TrainProvCatalogueService::qualVersionView).toList();
        return new QualificationDetail(qualView(t, q), vs);
    }

    @Transactional
    public QualificationView createQualification(TenantId t, QualificationRequest r, UUID by) {
        checkAuthority(t, r.authorityId());
        checkRef(t, r.officialRef(), null);
        TrainProvQualification q = guard(() -> TrainProvQualification.create(t, r.title(), r.qualificationType(), r.officialRef(),
                r.saqaId(), r.subFramework(), r.nqfLevel(), r.credits(), r.authorityId(), by));
        qualifications.save(q);
        return qualView(t, q);
    }

    @Transactional
    public QualificationView updateQualification(TenantId t, UUID id, QualificationRequest r) {
        TrainProvQualification q = qualification(t, id, true);
        checkAuthority(t, r.authorityId());
        checkRef(t, r.officialRef(), id);
        guard(() -> { q.update(r.title(), r.qualificationType(), r.officialRef(), r.saqaId(), r.subFramework(),
                r.nqfLevel(), r.credits(), r.authorityId()); return null; });
        qualifications.save(q);
        return qualView(t, q);
    }

    @Transactional
    public QualificationView retireQualification(TenantId t, UUID id, String reason, UUID by) {
        TrainProvQualification q = qualification(t, id, true);
        long live = programmes.countLiveForQualification(t, id);
        if (live > 0) {
            throw conflict("This qualification is still used by " + live + " programme(s). Retire those first", "TP_QUALIFICATION_IN_USE");
        }
        guard(() -> { q.retire(reason, by); return null; });
        qualifications.save(q);
        return qualView(t, q);
    }

    /** Starts a draft version, copying the latest version's details so only what changed needs editing. */
    @Transactional
    public QualificationVersionView newQualificationVersion(TenantId t, UUID qualificationId, UUID by) {
        qualification(t, qualificationId, true);
        List<TrainProvQualificationVersion> existing = qualVersions.findForQualification(t, qualificationId);
        if (existing.stream().anyMatch(v -> "DRAFT".equals(v.getStatus()))) {
            throw conflict("There is already a draft version. Finish or use that one", "TP_VERSION_DRAFT_EXISTS");
        }
        TrainProvQualificationVersion v = TrainProvQualificationVersion.draft(t, qualificationId, qualVersions.maxVersionNo(t, qualificationId) + 1, by);
        if (!existing.isEmpty()) {
            TrainProvQualificationVersion last = existing.get(0);
            v.edit(last.getRegistrationStatus(), last.getRegistrationStart(), last.getRegistrationEnd(), last.getEntryRequirements(),
                    last.getExitOutcomes(), last.getRulesOfCombination(), null, null);
        }
        qualVersions.save(v);
        return qualVersionView(v);
    }

    @Transactional
    public QualificationVersionView editQualificationVersion(TenantId t, UUID versionId, QualificationVersionRequest r) {
        TrainProvQualificationVersion v = qualVersion(t, versionId);
        guard(() -> { v.edit(r.registrationStatus(), r.registrationStart(), r.registrationEnd(), r.entryRequirements(),
                r.exitOutcomes(), r.rulesOfCombination(), r.sourceNote(), r.effectiveFrom()); return null; });
        qualVersions.save(v);
        return qualVersionView(v);
    }

    /** Publishes a draft as the current version. The previous current version is superseded and kept. */
    @Transactional
    public QualificationVersionView makeQualificationVersionCurrent(TenantId t, UUID versionId, UUID by, LocalDate today) {
        TrainProvQualificationVersion v = qualVersion(t, versionId);
        String problem = CatalogueRules.qualificationActivationProblem(v.getSourceNote(), v.getRegistrationStatus(), v.getRegistrationEnd(), today);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_QUALIFICATION_VERSION_INVALID");
        }
        qualVersions.findCurrent(t, v.getQualificationId()).ifPresent(prev -> {
            prev.supersede();
            qualVersions.saveAndFlush(prev);
        });
        guard(() -> { v.makeCurrent(by); return null; });
        qualVersions.save(v);
        return qualVersionView(v);
    }

    // ---- programmes --------------------------------------------------------

    public List<ProgrammeView> programmes(TenantId t, String status, String activity, LocalDate today) {
        return programmes.findAllForTenant(t, status, activity).stream().<ProgrammeView>map(p -> programmeView(t, p, today)).toList();
    }

    public ProgrammeDetail programme(TenantId t, UUID id, LocalDate today) {
        TrainProvProgramme p = programme(t, id);
        List<ProgrammeVersionView> vs = versions.findForProgramme(t, id).stream()
                .<ProgrammeVersionView>map(v -> versionView(t, v)).toList();
        return new ProgrammeDetail(programmeView(t, p, today), vs, offeringViews(t, p, today));
    }

    @Transactional
    public ProgrammeView createProgramme(TenantId t, ProgrammeRequest r, UUID by, LocalDate today) {
        checkProgrammeRefs(t, r);
        if (programmes.codeTaken(t, r.code().trim(), null)) {
            throw conflict("You already have a programme with that code", "TP_PROGRAMME_CODE");
        }
        TrainProvProgramme p = guard(() -> TrainProvProgramme.create(t, r.code(), r.title(), r.programmeType(), r.trainingActivity(),
                r.deliveryMode(), r.qualificationId(), r.courseId(), r.description(), by));
        programmes.save(p);
        return programmeView(t, p, today);
    }

    @Transactional
    public ProgrammeView updateProgramme(TenantId t, UUID id, ProgrammeRequest r, LocalDate today) {
        TrainProvProgramme p = programme(t, id);
        checkProgrammeRefs(t, r);
        if (programmes.codeTaken(t, r.code().trim(), id)) {
            throw conflict("You already have a programme with that code", "TP_PROGRAMME_CODE");
        }
        guard(() -> { p.update(r.code(), r.title(), r.programmeType(), r.trainingActivity(), r.deliveryMode(),
                r.qualificationId(), r.courseId(), r.description()); return null; });
        programmes.save(p);
        return programmeView(t, p, today);
    }

    /** Makes a programme available to be offered. It needs an approved version first. */
    @Transactional
    public ProgrammeView activateProgramme(TenantId t, UUID id, LocalDate today) {
        TrainProvProgramme p = programme(t, id);
        if (versions.findApproved(t, id).isEmpty()) {
            throw conflict("Approve a version of this programme first", "TP_PROGRAMME_NO_APPROVED_VERSION");
        }
        guard(() -> { p.activate(); return null; });
        programmes.save(p);
        return programmeView(t, p, today);
    }

    @Transactional
    public ProgrammeView retireProgramme(TenantId t, UUID id, String reason, UUID by, LocalDate today) {
        TrainProvProgramme p = programme(t, id);
        long live = offerings.countLive(t, id);
        if (live > 0) {
            throw conflict("This programme is still offered at " + live + " site(s). Withdraw those first", "TP_PROGRAMME_IN_USE");
        }
        guard(() -> { p.retire(reason, by); return null; });
        programmes.save(p);
        return programmeView(t, p, today);
    }

    // ---- programme versions and modules ----------------------------------------

    /** Starts a draft version, copying the latest version's details and modules. */
    @Transactional
    public ProgrammeVersionView newProgrammeVersion(TenantId t, UUID programmeId, UUID by) {
        TrainProvProgramme p = programme(t, programmeId);
        if ("RETIRED".equals(p.getStatus())) {
            throw conflict("This programme is retired", "TP_PROGRAMME_RETIRED");
        }
        List<TrainProvProgrammeVersion> existing = versions.findForProgramme(t, programmeId);
        if (existing.stream().anyMatch(v -> "DRAFT".equals(v.getStatus()))) {
            throw conflict("There is already a draft version. Finish or use that one", "TP_VERSION_DRAFT_EXISTS");
        }
        TrainProvProgrammeVersion v = TrainProvProgrammeVersion.draft(t, programmeId, versions.maxVersionNo(t, programmeId) + 1, by);
        if (!existing.isEmpty()) {
            TrainProvProgrammeVersion last = existing.get(0);
            v.edit(last.getQualificationVersionId(), last.getLearningHours(), last.getCredits(), last.getSummary(), null);
        }
        versions.save(v);
        if (!existing.isEmpty()) {
            for (TrainProvProgrammeModule m : modules.findForVersion(t, existing.get(0).getId())) {
                modules.save(TrainProvProgrammeModule.create(t, v.getId(), m.getSeq(), m.getTitle(), m.getModuleType(),
                        m.getCredits(), m.getNotionalHours(), m.isRequired(), m.getLearningOutcomes()));
            }
        }
        return versionView(t, v);
    }

    @Transactional
    public ProgrammeVersionView editProgrammeVersion(TenantId t, UUID versionId, ProgrammeVersionRequest r) {
        TrainProvProgrammeVersion v = version(t, versionId);
        if (r.qualificationVersionId() != null) {
            qualVersion(t, r.qualificationVersionId());
        }
        guard(() -> { v.edit(r.qualificationVersionId(), r.learningHours(), r.credits(), r.summary(), r.effectiveFrom()); return null; });
        versions.save(v);
        return versionView(t, v);
    }

    @Transactional
    public ProgrammeVersionView addModule(TenantId t, UUID versionId, ModuleRequest r) {
        TrainProvProgrammeVersion v = version(t, versionId);
        guard(() -> { v.requireDraft(); return null; });
        int seq = modules.maxSeq(t, versionId) + 1;
        modules.save(guard(() -> TrainProvProgrammeModule.create(t, versionId, seq, r.title(), r.moduleType(), r.credits(),
                r.notionalHours(), r.required() == null || r.required(), r.learningOutcomes())));
        return versionView(t, v);
    }

    /** Only a draft version's modules can be removed; an approved version is frozen. */
    @Transactional
    public ProgrammeVersionView removeModule(TenantId t, UUID moduleId) {
        TrainProvProgrammeModule m = modules.findForTenant(t, moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeModule", moduleId.toString()));
        TrainProvProgrammeVersion v = version(t, m.getProgrammeVersionId());
        guard(() -> { v.requireDraft(); return null; });
        modules.delete(m);
        return versionView(t, v);
    }

    /**
     * Approves a draft version. A second person approves, or the same person
     * says why not. The previously approved version is superseded and kept.
     */
    @Transactional
    public ProgrammeVersionView approveVersion(TenantId t, UUID versionId, String overrideReason, UUID by) {
        TrainProvProgrammeVersion v = version(t, versionId);
        TrainProvProgramme p = programme(t, v.getProgrammeId());
        guard(() -> { v.requireDraft(); return null; });
        UUID pinnedQual = null;
        String pinnedStatus = null;
        if (v.getQualificationVersionId() != null) {
            TrainProvQualificationVersion qv = qualVersion(t, v.getQualificationVersionId());
            pinnedQual = qv.getQualificationId();
            pinnedStatus = qv.getStatus();
        }
        int moduleCount = modules.findForVersion(t, versionId).size();
        String problem = CatalogueRules.versionApprovalProblem(p.getTrainingActivity(), moduleCount, p.getQualificationId(),
                pinnedQual, pinnedStatus, by, v.getCreatedBy(), overrideReason);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_PROGRAMME_VERSION_INVALID");
        }
        versions.findApproved(t, p.getId()).ifPresent(prev -> {
            prev.supersede();
            versions.saveAndFlush(prev);
        });
        guard(() -> { v.approve(by, overrideReason); return null; });
        versions.save(v);
        return versionView(t, v);
    }

    // ---- offerings ---------------------------------------------------------

    /**
     * Offers a programme at a site. An accredited programme is only offered
     * where a current approval covers it at that site; nothing else is
     * enough, and a submitted application never is.
     */
    @Transactional
    public OfferingView addOffering(TenantId t, UUID programmeId, OfferingRequest r, UUID by, LocalDate today) {
        TrainProvProgramme p = programme(t, programmeId);
        if (!"ACTIVE".equals(p.getStatus())) {
            throw conflict("Only an active programme can be offered. Approve a version and activate it first", "TP_PROGRAMME_NOT_ACTIVE");
        }
        TrainProvDeliverySite site = sites.findForTenant(t, r.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvDeliverySite", r.siteId().toString()));
        if (!"ACTIVE".equals(site.getStatus())) {
            throw conflict("That site is retired", "TP_SITE_RETIRED");
        }
        if (offerings.liveExists(t, programmeId, r.siteId())) {
            throw conflict("This programme is already offered at that site", "TP_OFFERING_EXISTS");
        }
        LocalDate from = r.offeredFrom() == null ? today : r.offeredFrom();
        String note = null;
        if (CatalogueRules.ACCREDITED.equals(p.getTrainingActivity())) {
            CoverageView c = accreditation.programmeCoverage(t, programmeId, p.getCourseId(), r.siteId(), from);
            if (!c.covered()) {
                throw new HandyFlowException("Not offered: " + c.message(), HttpStatus.CONFLICT, "TP_NOT_ACCREDITED_FOR_SITE");
            }
            note = c.message();
        }
        TrainProvProgrammeOffering o = TrainProvProgrammeOffering.create(t, programmeId, r.siteId(), from, note, by);
        offerings.save(o);
        return offeringView(t, o, p, site.getName(), today);
    }

    @Transactional
    public OfferingView withdrawOffering(TenantId t, UUID offeringId, String reason, UUID by, LocalDate today) {
        TrainProvProgrammeOffering o = offerings.findForTenant(t, offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeOffering", offeringId.toString()));
        guard(() -> { o.withdraw(reason, by); return null; });
        offerings.save(o);
        TrainProvProgramme p = programme(t, o.getProgrammeId());
        String siteName = sites.findForTenant(t, o.getSiteId()).map(TrainProvDeliverySite::getName).orElse("");
        return offeringView(t, o, p, siteName, today);
    }

    // ---- views and helpers ---------------------------------------------------

    private ProgrammeView programmeView(TenantId t, TrainProvProgramme p, LocalDate today) {
        Integer approvedNo = versions.findApproved(t, p.getId()).map(TrainProvProgrammeVersion::getVersionNo).orElse(null);
        String qualTitle = p.getQualificationId() == null ? null
                : qualifications.findForTenant(t, p.getQualificationId()).map(TrainProvQualification::getTitle).orElse(null);
        long live = offerings.countLive(t, p.getId());
        return new ProgrammeView(p.getId(), p.getCode(), p.getTitle(), p.getProgrammeType(), p.getTrainingActivity(),
                p.getDeliveryMode(), p.getQualificationId(), qualTitle, p.getCourseId(), p.getDescription(), p.getStatus(),
                p.getRetiredReason(), approvedNo, live, advertisableAsAccredited(t, p, today));
    }

    /**
     * True only for an active accredited programme with at least one offering
     * whose site is covered by a current approval today. Anything else must not
     * be described to the public as accredited.
     */
    boolean advertisableAsAccredited(TenantId t, TrainProvProgramme p, LocalDate today) {
        if (!CatalogueRules.ACCREDITED.equals(p.getTrainingActivity()) || !"ACTIVE".equals(p.getStatus())) {
            return false;
        }
        for (TrainProvProgrammeOffering o : offerings.findForProgramme(t, p.getId())) {
            if ("ACTIVE".equals(o.getStatus())
                    && accreditation.programmeCoverage(t, p.getId(), p.getCourseId(), o.getSiteId(), today).covered()) {
                return true;
            }
        }
        return false;
    }

    private List<OfferingView> offeringViews(TenantId t, TrainProvProgramme p, LocalDate today) {
        Map<UUID, String> names = sites.findAllForTenant(t).stream()
                .collect(Collectors.toMap(TrainProvDeliverySite::getId, TrainProvDeliverySite::getName));
        return offerings.findForProgramme(t, p.getId()).stream()
                .<OfferingView>map(o -> offeringView(t, o, p, names.getOrDefault(o.getSiteId(), ""), today)).toList();
    }

    private OfferingView offeringView(TenantId t, TrainProvProgrammeOffering o, TrainProvProgramme p, String siteName, LocalDate today) {
        boolean accredited = CatalogueRules.ACCREDITED.equals(p.getTrainingActivity());
        CoverageView c = accredited && "ACTIVE".equals(o.getStatus())
                ? accreditation.programmeCoverage(t, p.getId(), p.getCourseId(), o.getSiteId(), today) : null;
        return new OfferingView(o.getId(), o.getProgrammeId(), o.getSiteId(), siteName, o.getStatus(), o.getOfferedFrom(),
                o.getCoverageNote(), c == null || c.covered(), c == null ? null : c.outcome(), c == null ? null : c.message(),
                o.getWithdrawnReason());
    }

    private ProgrammeVersionView versionView(TenantId t, TrainProvProgrammeVersion v) {
        List<TrainProvProgrammeModule> ms = modules.findForVersion(t, v.getId());
        int required = ms.stream().filter(TrainProvProgrammeModule::isRequired).mapToInt(m -> m.getCredits() == null ? 0 : m.getCredits()).sum();
        int all = ms.stream().mapToInt(m -> m.getCredits() == null ? 0 : m.getCredits()).sum();
        List<ModuleView> mv = ms.stream().<ModuleView>map(m -> new ModuleView(m.getId(), m.getSeq(), m.getTitle(), m.getModuleType(),
                m.getCredits(), m.getNotionalHours(), m.isRequired(), m.getLearningOutcomes())).toList();
        return new ProgrammeVersionView(v.getId(), v.getVersionNo(), v.getStatus(), v.getQualificationVersionId(), v.getLearningHours(),
                v.getCredits(), v.getSummary(), v.getEffectiveFrom(), v.getApprovedAt(), v.getApprovedBy(),
                v.getApprovalOverrideReason(), CatalogueRules.creditsNote(v.getCredits(), required, all), mv);
    }

    private QualificationView qualView(TenantId t, TrainProvQualification q) {
        List<TrainProvQualificationVersion> vs = qualVersions.findForQualification(t, q.getId());
        Integer current = vs.stream().filter(v -> "CURRENT".equals(v.getStatus())).map(TrainProvQualificationVersion::getVersionNo).findFirst().orElse(null);
        return new QualificationView(q.getId(), q.getTitle(), q.getQualificationType(), q.getOfficialRef(), q.getSaqaId(), q.getSubFramework(),
                q.getNqfLevel(), q.getCredits(), q.getAuthorityId(), q.getStatus(), q.getRetiredReason(), current, vs.size());
    }

    private static QualificationVersionView qualVersionView(TrainProvQualificationVersion v) {
        return new QualificationVersionView(v.getId(), v.getQualificationId(), v.getVersionNo(), v.getStatus(), v.getRegistrationStatus(),
                v.getRegistrationStart(), v.getRegistrationEnd(), v.getEntryRequirements(), v.getExitOutcomes(), v.getRulesOfCombination(),
                v.getSourceNote(), v.getEffectiveFrom(), v.getMadeCurrentAt());
    }

    private TrainProvQualification qualification(TenantId t, UUID id, boolean any) {
        return qualifications.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvQualification", id.toString()));
    }

    private TrainProvQualificationVersion qualVersion(TenantId t, UUID id) {
        return qualVersions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvQualificationVersion", id.toString()));
    }

    private TrainProvProgramme programme(TenantId t, UUID id) {
        return programmes.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", id.toString()));
    }

    private TrainProvProgrammeVersion version(TenantId t, UUID id) {
        return versions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeVersion", id.toString()));
    }

    private void checkAuthority(TenantId t, UUID authorityId) {
        if (authorityId != null) {
            authorities.findForTenant(t, authorityId).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuthority", authorityId.toString()));
        }
    }

    private void checkRef(TenantId t, String ref, UUID excludeId) {
        if (ref != null && !ref.isBlank() && qualifications.refTaken(t, ref.trim(), excludeId)) {
            throw conflict("You already have a qualification with that official reference", "TP_QUALIFICATION_REF");
        }
    }

    private void checkProgrammeRefs(TenantId t, ProgrammeRequest r) {
        if (r.qualificationId() != null) {
            TrainProvQualification q = qualification(t, r.qualificationId(), true);
            if (!"ACTIVE".equals(q.getStatus())) {
                throw conflict("That qualification is retired", "TP_QUALIFICATION_RETIRED");
            }
        }
        if (r.courseId() != null) {
            courses.findActiveById(t, r.courseId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvCourse", r.courseId().toString()));
        }
    }

    private static HandyFlowException conflict(String message, String code) {
        return new HandyFlowException(message, HttpStatus.CONFLICT, code);
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_CATALOGUE_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_CATALOGUE_INVALID");
        }
    }
}

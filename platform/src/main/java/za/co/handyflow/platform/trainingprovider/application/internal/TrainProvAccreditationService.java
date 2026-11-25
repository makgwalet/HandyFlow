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
import za.co.handyflow.platform.trainingprovider.domain.rules.AccreditationRules;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Authorities, delivery sites, accreditations and what each one covers.
 * Records are retired or withdrawn with a reason, never deleted. An approval
 * is recorded by an administrator with the authority's number and where it
 * was verified, and a second person should confirm it. Nothing here claims
 * the provider is accredited because a number was typed in: delivery is
 * only supported when {@link #coverage} says so for a programme at a site.
 */
@Service
@RequiredArgsConstructor
public class TrainProvAccreditationService {

    private final TrainProvAuthorityRepository authorities;
    private final TrainProvDeliverySiteRepository sites;
    private final TrainProvAccreditationRepository accreditations;
    private final TrainProvAccreditationScopeRepository scopes;
    private final TrainProvAccreditationEventRepository events;
    private final TrainProvCourseRepository courses;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeOfferingRepository offerings;

    // ---- authorities -------------------------------------------------------

    public List<AuthorityView> authorities(TenantId t) {
        return authorities.findAllForTenant(t).stream().map(TrainProvAccreditationService::authorityView).toList();
    }

    @Transactional
    public AuthorityView createAuthority(TenantId t, AuthorityRequest r) {
        if (authorities.nameTaken(t, r.name().trim(), null)) {
            throw conflict("You already have an authority with that name", "TP_AUTHORITY_NAME");
        }
        return authorityView(authorities.save(guard(() -> TrainProvAuthority.create(t, r.name(), r.authorityType(), r.notes()))));
    }

    @Transactional
    public AuthorityView updateAuthority(TenantId t, UUID id, AuthorityRequest r) {
        TrainProvAuthority a = authority(t, id);
        if (authorities.nameTaken(t, r.name().trim(), id)) {
            throw conflict("You already have an authority with that name", "TP_AUTHORITY_NAME");
        }
        guard(() -> { a.update(r.name(), r.authorityType(), r.notes()); return null; });
        return authorityView(authorities.save(a));
    }

    @Transactional
    public AuthorityView retireAuthority(TenantId t, UUID id, String reason, UUID by) {
        TrainProvAuthority a = authority(t, id);
        long live = accreditations.countLiveForAuthority(t, id);
        if (live > 0) {
            throw conflict("This authority still has " + live + " live accreditation(s). Withdraw or close those first", "TP_AUTHORITY_IN_USE");
        }
        guard(() -> { a.retire(reason, by); return null; });
        return authorityView(authorities.save(a));
    }

    // ---- delivery sites ----------------------------------------------------

    public List<SiteView> sites(TenantId t) {
        return sites.findAllForTenant(t).stream().map(TrainProvAccreditationService::siteView).toList();
    }

    @Transactional
    public SiteView createSite(TenantId t, SiteRequest r) {
        if (sites.nameTaken(t, r.name().trim(), null)) {
            throw conflict("You already have a site with that name", "TP_SITE_NAME");
        }
        return siteView(sites.save(guard(() -> TrainProvDeliverySite.create(t, r.name(), r.siteType(), r.address(),
                r.province(), r.contactPerson(), r.phone()))));
    }

    @Transactional
    public SiteView updateSite(TenantId t, UUID id, SiteRequest r) {
        TrainProvDeliverySite s = site(t, id);
        if (sites.nameTaken(t, r.name().trim(), id)) {
            throw conflict("You already have a site with that name", "TP_SITE_NAME");
        }
        guard(() -> { s.update(r.name(), r.siteType(), r.address(), r.province(), r.contactPerson(), r.phone()); return null; });
        return siteView(sites.save(s));
    }

    @Transactional
    public SiteView retireSite(TenantId t, UUID id, String reason, UUID by) {
        TrainProvDeliverySite s = site(t, id);
        long live = scopes.countLiveForSite(t, id);
        if (live > 0) {
            throw conflict("This site is still named in " + live + " live approval scope(s). Withdraw those first", "TP_SITE_IN_USE");
        }
        long offered = offerings.countLiveForSite(t, id);
        if (offered > 0) {
            throw conflict("This site still has " + offered + " programme offering(s). Withdraw those first", "TP_SITE_IN_USE");
        }
        guard(() -> { s.retire(reason, by); return null; });
        return siteView(sites.save(s));
    }

    // ---- accreditations ----------------------------------------------------

    public List<AccreditationView> list(TenantId t, String status, UUID authorityId, LocalDate today) {
        Map<UUID, String> names = authorityNames(t);
        return accreditations.findAllForTenant(t, status, authorityId).stream()
                .<AccreditationView>map(a -> accreditationView(a, names, today, liveScopeCount(t, a.getId()))).toList();
    }

    public AccreditationDetail detail(TenantId t, UUID id, LocalDate today) {
        TrainProvAccreditation a = accreditation(t, id);
        List<ScopeView> scopeViews = scopeViews(t, id);
        int live = (int) scopeViews.stream().filter(s -> "ACTIVE".equals(s.status())).count();
        List<EventView> history = events.findForAccreditation(t, id).stream()
                .map(e -> new EventView(e.getId(), e.getFromStatus(), e.getToStatus(), e.getReason(),
                        e.getOverrideReason(), e.getActedBy(), e.getActedAt())).toList();
        return new AccreditationDetail(accreditationView(a, authorityNames(t), today, live), scopeViews, history);
    }

    @Transactional
    public AccreditationView create(TenantId t, CreateAccreditationRequest r, UUID by, LocalDate today) {
        TrainProvAuthority auth = authority(t, r.authorityId());
        if (!"ACTIVE".equals(auth.getStatus())) {
            throw conflict("That authority is retired", "TP_AUTHORITY_RETIRED");
        }
        checkNumberFree(t, auth.getId(), r.accreditationNumber(), null);
        TrainProvAccreditation a = guard(() -> TrainProvAccreditation.create(t, auth.getId(), r.title(), r.accreditationNumber(), by));
        accreditations.save(a);
        events.save(TrainProvAccreditationEvent.of(t, a.getId(), null, a.getStatus(), "Created", null, by));
        return accreditationView(a, Map.of(auth.getId(), auth.getName()), today, 0);
    }

    @Transactional
    public AccreditationView edit(TenantId t, UUID id, EditAccreditationRequest r, LocalDate today) {
        TrainProvAccreditation a = accreditation(t, id);
        checkNumberFree(t, a.getAuthorityId(), r.accreditationNumber(), id);
        guard(() -> { a.editDetails(r.title(), r.accreditationNumber(), r.expiryDate(), r.conditions()); return null; });
        accreditations.save(a);
        return accreditationView(a, authorityNames(t), today, liveScopeCount(t, id));
    }

    /**
     * Moves an accreditation along its lifecycle. Recording an approval is for
     * administrators only, and needs the authority's number, an approval date,
     * where it was verified, and either a second person or a stated reason.
     */
    @Transactional
    public AccreditationView transition(TenantId t, UUID id, TransitionRequest r, UUID by, boolean admin, LocalDate today) {
        TrainProvAccreditation a = accreditation(t, id);
        String to = r.toStatus();
        if (AccreditationRules.isApprovalState(to) && !admin) {
            throw new HandyFlowException("Only an administrator can record an approval", HttpStatus.FORBIDDEN, "TP_APPROVAL_ADMIN_ONLY");
        }
        String number = r.accreditationNumber() != null && !r.accreditationNumber().isBlank() ? r.accreditationNumber() : a.getAccreditationNumber();
        String problem = AccreditationRules.transitionProblem(a.getStatus(), to, r.reason(), number, r.approvalDate(),
                r.expiryDate(), r.conditions(), r.verificationSource(), by, a.getCreatedBy(), r.overrideReason(), today);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_ACCREDITATION_TRANSITION");
        }
        if (AccreditationRules.isApprovalState(to)) {
            checkNumberFree(t, a.getAuthorityId(), number, id);
        }
        String from = a.getStatus();
        guard(() -> { a.applyTransition(to, r.reason(), number, r.approvalDate(), r.expiryDate(), r.conditions(),
                r.verificationSource(), r.verifiedOn(), by, today); return null; });
        accreditations.save(a);
        boolean override = AccreditationRules.isApprovalState(to) && by.equals(a.getCreatedBy());
        events.save(TrainProvAccreditationEvent.of(t, id, from, to, r.reason(), override ? r.overrideReason() : null, by));
        return accreditationView(a, authorityNames(t), today, liveScopeCount(t, id));
    }

    @Transactional
    public AccreditationView verify(TenantId t, UUID id, VerifyRequest r, UUID by, LocalDate today) {
        TrainProvAccreditation a = accreditation(t, id);
        guard(() -> { a.verify(r.verificationSource(), r.verifiedOn() == null ? today : r.verifiedOn(), by); return null; });
        accreditations.save(a);
        events.save(TrainProvAccreditationEvent.of(t, id, a.getStatus(), a.getStatus(), "Verified: " + r.verificationSource().trim(), null, by));
        return accreditationView(a, authorityNames(t), today, liveScopeCount(t, id));
    }

    // ---- scope -------------------------------------------------------------

    @Transactional
    public ScopeView addScope(TenantId t, UUID accreditationId, ScopeRequest r, UUID by) {
        TrainProvAccreditation a = accreditation(t, accreditationId);
        if (AccreditationRules.isTerminal(a.getStatus())) {
            throw conflict("This accreditation is closed", "TP_ACCREDITATION_STATE");
        }
        TrainProvDeliverySite s = site(t, r.siteId());
        if (!"ACTIVE".equals(s.getStatus())) {
            throw conflict("That site is retired", "TP_SITE_RETIRED");
        }
        if (r.courseId() != null) {
            courses.findActiveById(t, r.courseId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvCourse", r.courseId().toString()));
        }
        if (r.programmeId() != null) {
            programmes.findForTenant(t, r.programmeId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", r.programmeId().toString()));
        }
        String ref = r.qualificationRef() == null || r.qualificationRef().isBlank() ? null : r.qualificationRef().trim();
        if (scopes.liveDuplicate(t, accreditationId, r.siteId(), r.programmeTitle().trim(), ref)) {
            throw conflict("That programme is already in this approval at that site", "TP_SCOPE_DUPLICATE");
        }
        TrainProvAccreditationScope sc = guard(() -> TrainProvAccreditationScope.create(t, accreditationId, r.siteId(),
                r.courseId(), r.programmeId(), r.programmeTitle(), r.qualificationRef(), r.nqfLevel(), r.credits(), r.validFrom(),
                r.validTo(), r.notes(), by));
        scopes.save(sc);
        return scopeView(sc, s.getName());
    }

    @Transactional
    public ScopeView withdrawScope(TenantId t, UUID scopeId, String reason, UUID by) {
        TrainProvAccreditationScope sc = scopes.findForTenant(t, scopeId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvAccreditationScope", scopeId.toString()));
        guard(() -> { sc.withdraw(reason, by); return null; });
        scopes.save(sc);
        return scopeView(sc, site(t, sc.getSiteId()).getName());
    }

    /**
     * Whether a course can be delivered at a site on a day. This is the check
     * that publishing an accredited course or enrolling a learner must pass.
     */
    public CoverageView coverage(TenantId t, UUID courseId, UUID siteId, LocalDate day) {
        courses.findActiveById(t, courseId).orElseThrow(() -> new ResourceNotFoundException("TrainProvCourse", courseId.toString()));
        site(t, siteId);
        return evaluate(t, scopes.findForCourse(t, courseId), courseId, siteId, day);
    }

    /** The same check for a catalogue programme: scopes that name the programme, or the course it is linked to. */
    public CoverageView programmeCoverage(TenantId t, UUID programmeId, UUID courseId, UUID siteId, LocalDate day) {
        return evaluate(t, scopes.findForProgramme(t, programmeId, courseId), programmeId, siteId, day);
    }

    private CoverageView evaluate(TenantId t, List<TrainProvAccreditationScope> found, UUID subjectId, UUID siteId, LocalDate day) {
        Map<UUID, TrainProvAccreditation> byId = accreditations.findAllById(
                found.stream().map(TrainProvAccreditationScope::getAccreditationId).collect(Collectors.toSet()))
                .stream().filter(a -> a.getTenantId().equals(t.getValue()))
                .collect(Collectors.toMap(TrainProvAccreditation::getId, Function.identity()));
        List<AccreditationRules.ScopeFacts> facts = found.stream()
                .filter(sc -> byId.containsKey(sc.getAccreditationId()))
                .map(sc -> {
                    TrainProvAccreditation a = byId.get(sc.getAccreditationId());
                    return new AccreditationRules.ScopeFacts(a.getStatus(), a.getExpiryDate(), sc.getStatus(),
                            sc.getValidFrom(), sc.getValidTo(), sc.getSiteId());
                }).toList();
        AccreditationRules.Coverage c = AccreditationRules.best(facts, siteId, day);
        return new CoverageView(subjectId, siteId, day, c.outcome().name(), c.covered(), c.message());
    }

    // ---- helpers -----------------------------------------------------------

    private TrainProvAuthority authority(TenantId t, UUID id) {
        return authorities.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuthority", id.toString()));
    }

    private TrainProvDeliverySite site(TenantId t, UUID id) {
        return sites.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvDeliverySite", id.toString()));
    }

    private TrainProvAccreditation accreditation(TenantId t, UUID id) {
        return accreditations.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAccreditation", id.toString()));
    }

    private void checkNumberFree(TenantId t, UUID authorityId, String number, UUID excludeId) {
        if (number != null && !number.isBlank() && accreditations.numberTaken(t, authorityId, number.trim(), excludeId)) {
            throw conflict("That accreditation number is already recorded for this authority", "TP_ACCREDITATION_NUMBER");
        }
    }

    private Map<UUID, String> authorityNames(TenantId t) {
        return authorities.findAllForTenant(t).stream().collect(Collectors.toMap(TrainProvAuthority::getId, TrainProvAuthority::getName));
    }

    private int liveScopeCount(TenantId t, UUID accreditationId) {
        return (int) scopes.findForAccreditation(t, accreditationId).stream().filter(s -> "ACTIVE".equals(s.getStatus())).count();
    }

    private List<ScopeView> scopeViews(TenantId t, UUID accreditationId) {
        Map<UUID, String> siteNames = sites.findAllForTenant(t).stream()
                .collect(Collectors.toMap(TrainProvDeliverySite::getId, TrainProvDeliverySite::getName));
        return scopes.findForAccreditation(t, accreditationId).stream()
                .<ScopeView>map(s -> scopeView(s, siteNames.getOrDefault(s.getSiteId(), ""))).toList();
    }

    private static AuthorityView authorityView(TrainProvAuthority a) {
        return new AuthorityView(a.getId(), a.getName(), a.getAuthorityType(), a.getNotes(), a.getStatus(), a.getRetiredReason(), a.getRetiredAt());
    }

    private static SiteView siteView(TrainProvDeliverySite s) {
        return new SiteView(s.getId(), s.getName(), s.getSiteType(), s.getAddress(), s.getProvince(), s.getContactPerson(),
                s.getPhone(), s.getStatus(), s.getRetiredReason(), s.getRetiredAt());
    }

    private static ScopeView scopeView(TrainProvAccreditationScope s, String siteName) {
        return new ScopeView(s.getId(), s.getAccreditationId(), s.getSiteId(), siteName, s.getCourseId(), s.getProgrammeId(), s.getProgrammeTitle(),
                s.getQualificationRef(), s.getNqfLevel(), s.getCredits(), s.getValidFrom(), s.getValidTo(), s.getNotes(),
                s.getStatus(), s.getWithdrawnReason(), s.getWithdrawnAt());
    }

    static AccreditationView accreditationView(TrainProvAccreditation a, Map<UUID, String> names, LocalDate today, int liveScopes) {
        String effective = AccreditationRules.effectiveStatus(a.getStatus(), a.getExpiryDate(), today);
        Long days = a.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, a.getExpiryDate());
        List<String> next = AccreditationRules.nextStates(a.getStatus()).stream().sorted().toList();
        return new AccreditationView(a.getId(), a.getAuthorityId(), names.getOrDefault(a.getAuthorityId(), ""), a.getTitle(),
                a.getAccreditationNumber(), a.getStatus(), effective, a.getSubmittedOn(), a.getApprovalDate(), a.getExpiryDate(),
                days, a.getConditions(), a.getStatusReason(), a.getVerificationSource(), a.getLastVerifiedOn(), next,
                AccreditationRules.detailsEditable(a.getStatus()), liveScopes);
    }

    private static HandyFlowException conflict(String message, String code) {
        return new HandyFlowException(message, HttpStatus.CONFLICT, code);
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_ACCREDITATION_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_ACCREDITATION_INVALID");
        }
    }
}

package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.LitigationDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * The court case on a matter: its stage, its hearings and its orders. A hearing is also a court date on the matter's key-date calendar, and an
 * order with a date to comply by is a filing deadline there, so the existing reminders and the deadline screens pick them up. Nothing is deleted.
 * Ethical walls apply to every call, and the firm-wide case list and court roll leave out walled matters and clients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpLitigationService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final int EVENTS_SHOWN = 100;

    private final LpLitigationCaseRepository caseRepo;
    private final LpHearingRepository hearingRepo;
    private final LpCourtOrderRepository orderRepo;
    private final LpLitigationEventRepository eventRepo;
    private final LpMatterKeyDateRepository keyDateRepo;
    private final LpMatterRepository matterRepo;
    private final LpWallGuard walls;

    private static LocalDate today() { return LocalDate.now(ZONE); }

    // ---- reading -------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MatterLitigation forMatter(TenantId tenantId, UUID matterId) {
        walls.requireMatterAccess(tenantId, matterId);
        LpMatter matter = findMatter(tenantId, matterId);
        return new MatterLitigation(caseRepo.findForMatter(tenantId, matterId).map(c -> detail(c, label(matter), List.of())).orElse(null));
    }

    @Transactional(readOnly = true)
    public CaseDetail get(TenantId tenantId, UUID id) {
        LpLitigationCase c = find(tenantId, id);
        return detail(c, label(findMatter(tenantId, c.getMatterId())), List.of());
    }

    @Transactional(readOnly = true)
    public Page<CaseSummary> firmList(TenantId tenantId, String search, String stage, boolean includeConcluded, Pageable pageable) {
        var barred = walls.barred(tenantId);
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        Page<LpLitigationCase> page = caseRepo.findFirmExcluding(tenantId, barred.matterIds(), barred.clientIds(), includeConcluded, stage == null ? "" : stage, "%" + q + "%", pageable);
        Map<UUID, String> labels = labels(tenantId, page.getContent().stream().map(LpLitigationCase::getMatterId).collect(java.util.stream.Collectors.toSet()));
        LocalDate today = today();
        return page.map(c -> {
            LocalDate next = hearingRepo.findForCase(tenantId, c.getId()).stream().filter(h -> h.scheduled() && !h.getHearingDate().isBefore(today))
                    .map(LpHearing::getHearingDate).min(Comparator.naturalOrder()).orElse(null);
            return new CaseSummary(c.getId(), c.getMatterId(), labels.getOrDefault(c.getMatterId(), ""), c.getClientId(), c.getCourtLevel(), c.getCourtName(), c.getCaseNumber(),
                    c.getOurRole(), c.getStage(), c.getOpposingParty(), next, c.getOutcome(), c.getUpdatedAt());
        });
    }

    /** Scheduled hearings from {@code from} to {@code to}, across the firm. A person only sees hearings on matters they are not walled off from. */
    @Transactional(readOnly = true)
    public List<RollRow> courtRoll(TenantId tenantId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("Choose a start date and an end date after it");
        if (to.isAfter(from.plusDays(400))) throw new IllegalArgumentException("Choose a range of up to 400 days");
        var barred = walls.barred(tenantId);
        List<Object[]> rows = hearingRepo.findRollExcluding(tenantId, from, to, barred.matterIds(), barred.clientIds());
        Map<UUID, String> labels = labels(tenantId, rows.stream().map(r -> ((LpHearing) r[0]).getMatterId()).collect(java.util.stream.Collectors.toSet()));
        List<RollRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            LpHearing h = (LpHearing) r[0];
            LpLitigationCase c = (LpLitigationCase) r[1];
            out.add(new RollRow(h.getId(), c.getId(), c.getMatterId(), labels.getOrDefault(c.getMatterId(), ""), c.getCourtName(), c.getCaseNumber(), h.getHearingType(),
                    h.getHearingDate(), h.getHearingTime(), h.getCourtRoom(), h.getAppearing(), h.getPostponements(), LitigationRules.dateWarnings(h.getHearingDate())));
        }
        return out;
    }

    // ---- the case ------------------------------------------------------------------------------------------------------

    @Transactional
    public CaseDetail open(TenantId tenantId, OpenCaseRequest r, UUID by, String byName) {
        if (r.matterId() == null) throw new IllegalArgumentException("Choose the matter");
        walls.requireMatterAccess(tenantId, r.matterId());
        LpMatter matter = findMatter(tenantId, r.matterId());
        if (caseRepo.findForMatter(tenantId, matter.getId()).isPresent()) throw new IllegalStateException("This matter already has a court case");
        Instant now = Instant.now();
        String stage = r.stage() == null || r.stage().isBlank() ? "PRE_ACTION" : r.stage();
        if (!LitigationRules.STAGES.contains(stage) || "CONCLUDED".equals(stage)) throw new IllegalArgumentException("Choose the stage the case is at");
        LpLitigationCase c = LpLitigationCase.open(tenantId, matter.getId(), matter.getClientId(), r.courtLevel(), r.courtName(), r.ourRole(), r.caseNumber(), stage, r.opposingParty(),
                r.opposingAttorney(), r.opposingAttorneyContact(), r.judge(), r.issuedOn(), r.servedOn(), r.notes(), by, byName, today(), now);
        caseRepo.save(c);
        event(tenantId, c, "CASE_OPENED", by, byName, c.getCourtName() + (c.getCaseNumber() == null ? "" : ", " + c.getCaseNumber()) + " (" + stage + ")");
        log.info("Opened litigation case={} matter={} tenant={}", c.getId(), matter.getId(), tenantId);
        return detail(c, label(matter), List.of());
    }

    @Transactional
    public CaseDetail edit(TenantId tenantId, UUID id, EditCaseRequest r, UUID by, String byName) {
        LpLitigationCase c = find(tenantId, id);
        c.edit(r.courtLevel(), r.courtName(), r.ourRole(), r.caseNumber(), r.opposingParty(), r.opposingAttorney(), r.opposingAttorneyContact(), r.judge(), r.issuedOn(), r.servedOn(),
                r.notes(), today(), Instant.now());
        caseRepo.save(c);
        event(tenantId, c, "DETAILS_EDITED", by, byName, null);
        return detail(c, labelOf(tenantId, c), List.of());
    }

    @Transactional
    public CaseDetail changeStage(TenantId tenantId, UUID id, StageRequest r, UUID by, String byName) {
        LpLitigationCase c = find(tenantId, id);
        String from = c.changeStage(r.stage(), r.note(), Instant.now());
        caseRepo.save(c);
        event(tenantId, c, "STAGE_CHANGED", by, byName, from + " to " + c.getStage() + (r.note() == null || r.note().isBlank() ? "" : ": " + r.note().trim()));
        return detail(c, labelOf(tenantId, c), List.of());
    }

    @Transactional
    public CaseDetail conclude(TenantId tenantId, UUID id, ConcludeRequest r, UUID by, String byName) {
        LpLitigationCase c = find(tenantId, id);
        c.conclude(r.outcome(), r.concludedOn(), r.note(), (int) hearingRepo.countScheduled(tenantId, c.getId()), today(), Instant.now());
        caseRepo.save(c);
        event(tenantId, c, "CASE_CONCLUDED", by, byName, c.getOutcome() + ": " + c.getOutcomeNote());
        long open = orderRepo.findForCase(tenantId, c.getId()).stream().filter(LpCourtOrder::outstanding).count();
        List<String> warnings = open == 0 ? List.of() : List.of(open + (open == 1 ? " court order has" : " court orders have") + " not been marked as complied with");
        return detail(c, labelOf(tenantId, c), warnings);
    }

    @Transactional
    public CaseDetail reopen(TenantId tenantId, UUID id, ReopenRequest r, UUID by, String byName) {
        LpLitigationCase c = find(tenantId, id);
        c.reopen(r.stage(), r.reason(), Instant.now());
        caseRepo.save(c);
        event(tenantId, c, "CASE_REOPENED", by, byName, "At " + c.getStage() + ": " + r.reason().trim());
        return detail(c, labelOf(tenantId, c), List.of());
    }

    // ---- hearings ------------------------------------------------------------------------------------------------------

    @Transactional
    public CaseDetail scheduleHearing(TenantId tenantId, UUID caseId, HearingRequest r, UUID by, String byName) {
        LpLitigationCase c = find(tenantId, caseId);
        c.requireOpen();
        Instant now = Instant.now();
        LpHearing h = LpHearing.schedule(tenantId, c.getId(), c.getMatterId(), r.hearingType(), r.hearingDate(), r.hearingTime(), r.courtRoom(), r.appearing(), by, byName, now);
        LpMatterKeyDate kd = keyDateRepo.save(LpMatterKeyDate.create(tenantId, c.getMatterId(), "COURT_DATE", h.getHearingDate(),
                LitigationRules.hearingKeyDescription(h.getHearingType(), c.getCaseNumber(), c.getCourtName()), null));
        h.linkKeyDate(kd.getId());
        hearingRepo.save(h);
        event(tenantId, c, "HEARING_SET", by, byName, LitigationRules.hearingLabel(h.getHearingType()) + " on " + h.getHearingDate());
        return detail(c, labelOf(tenantId, c), LitigationRules.dateWarnings(h.getHearingDate()));
    }

    @Transactional
    public CaseDetail postponeHearing(TenantId tenantId, UUID hearingId, PostponeRequest r, UUID by, String byName) {
        LpHearing h = findHearing(tenantId, hearingId);
        LpLitigationCase c = find(tenantId, h.getCaseId());
        c.requireOpen();
        LocalDate was = h.postpone(r.hearingDate(), r.hearingTime(), r.reason(), Instant.now());
        hearingRepo.save(h);
        moveKeyDate(tenantId, h, c);
        event(tenantId, c, "HEARING_MOVED", by, byName, was + " to " + h.getHearingDate() + ": " + r.reason().trim());
        return detail(c, labelOf(tenantId, c), LitigationRules.dateWarnings(h.getHearingDate()));
    }

    @Transactional
    public CaseDetail holdHearing(TenantId tenantId, UUID hearingId, HeldRequest r, UUID by, String byName) {
        LpHearing h = findHearing(tenantId, hearingId);
        LpLitigationCase c = find(tenantId, h.getCaseId());
        h.hold(r.outcome(), today(), Instant.now());
        hearingRepo.save(h);
        completeKeyDate(tenantId, h.getKeyDateId());
        event(tenantId, c, "HEARING_HELD", by, byName, LitigationRules.hearingLabel(h.getHearingType()) + " of " + h.getHearingDate() + ": " + h.getOutcome());
        return detail(c, labelOf(tenantId, c), List.of());
    }

    @Transactional
    public CaseDetail cancelHearing(TenantId tenantId, UUID hearingId, CancelRequest r, UUID by, String byName) {
        LpHearing h = findHearing(tenantId, hearingId);
        LpLitigationCase c = find(tenantId, h.getCaseId());
        h.cancel(r.reason(), Instant.now());
        hearingRepo.save(h);
        completeKeyDate(tenantId, h.getKeyDateId());
        event(tenantId, c, "HEARING_CANCELLED", by, byName, LitigationRules.hearingLabel(h.getHearingType()) + " of " + h.getHearingDate() + ": " + h.getCancelReason());
        return detail(c, labelOf(tenantId, c), List.of());
    }

    // ---- orders --------------------------------------------------------------------------------------------------------

    @Transactional
    public CaseDetail recordOrder(TenantId tenantId, UUID caseId, OrderRequest r, UUID by, String byName) {
        LpLitigationCase c = find(tenantId, caseId);
        c.requireOpen();
        if (r.hearingId() != null) {
            LpHearing h = findHearing(tenantId, r.hearingId());
            if (!h.getCaseId().equals(c.getId())) throw new IllegalArgumentException("That hearing belongs to another case");
        }
        LpCourtOrder o = LpCourtOrder.record(tenantId, c.getId(), c.getMatterId(), r.hearingId(), r.orderDate(), r.summary(), r.complianceDue(), by, byName, today(), Instant.now());
        if (o.getComplianceDue() != null) {
            LpMatterKeyDate kd = keyDateRepo.save(LpMatterKeyDate.create(tenantId, c.getMatterId(), "FILING_DEADLINE", o.getComplianceDue(),
                    LitigationRules.complianceKeyDescription(o.getSummary()), null));
            o.linkKeyDate(kd.getId());
        }
        orderRepo.save(o);
        event(tenantId, c, "ORDER_RECORDED", by, byName, o.getSummary() + (o.getComplianceDue() == null ? "" : " (comply by " + o.getComplianceDue() + ")"));
        return detail(c, labelOf(tenantId, c), o.getComplianceDue() == null ? List.of() : LitigationRules.dateWarnings(o.getComplianceDue()));
    }

    @Transactional
    public CaseDetail markComplied(TenantId tenantId, UUID orderId, ComplyRequest r, UUID by, String byName) {
        LpCourtOrder o = orderRepo.findActiveById(tenantId, orderId).orElseThrow(() -> new ResourceNotFoundException("LpCourtOrder", orderId.toString()));
        LpLitigationCase c = find(tenantId, o.getCaseId());
        o.comply(r.compliedOn(), r.note(), today());
        orderRepo.save(o);
        completeKeyDate(tenantId, o.getKeyDateId());
        event(tenantId, c, "ORDER_COMPLIED", by, byName, "Complied on " + o.getCompliedOn() + (o.getCompliedNote() == null ? "" : ": " + o.getCompliedNote()));
        return detail(c, labelOf(tenantId, c), List.of());
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private void moveKeyDate(TenantId tenantId, LpHearing h, LpLitigationCase c) {
        if (h.getKeyDateId() == null) return;
        keyDateRepo.findActiveById(tenantId, h.getKeyDateId()).ifPresent(kd -> {
            kd.update("COURT_DATE", h.getHearingDate(), LitigationRules.hearingKeyDescription(h.getHearingType(), c.getCaseNumber(), c.getCourtName()), kd.getNotes());
            keyDateRepo.save(kd);
        });
    }

    private void completeKeyDate(TenantId tenantId, UUID keyDateId) {
        if (keyDateId == null) return;
        keyDateRepo.findActiveById(tenantId, keyDateId).ifPresent(kd -> {
            kd.complete();
            keyDateRepo.save(kd);
        });
    }

    private LpLitigationCase find(TenantId tenantId, UUID id) {
        LpLitigationCase c = caseRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpLitigationCase", id.toString()));
        walls.requireMatterAccess(tenantId, c.getMatterId());
        return c;
    }

    private LpHearing findHearing(TenantId tenantId, UUID id) {
        LpHearing h = hearingRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpHearing", id.toString()));
        walls.requireMatterAccess(tenantId, h.getMatterId());
        return h;
    }

    private LpMatter findMatter(TenantId tenantId, UUID matterId) {
        return matterRepo.findActiveById(tenantId, matterId).orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
    }

    private String labelOf(TenantId tenantId, LpLitigationCase c) { return label(findMatter(tenantId, c.getMatterId())); }

    private static String label(LpMatter m) { return m.getMatterNumber() + " " + m.getMatterName(); }

    private Map<UUID, String> labels(TenantId tenantId, Set<UUID> matterIds) {
        Map<UUID, String> out = new HashMap<>();
        if (matterIds.isEmpty()) return out;
        for (LpMatter m : matterRepo.findAllById(matterIds)) if (m.getTenantId().equals(tenantId)) out.put(m.getId(), label(m));
        return out;
    }

    private void event(TenantId tenantId, LpLitigationCase c, String type, UUID by, String byName, String detail) {
        eventRepo.save(LpLitigationEvent.record(tenantId, c.getId(), c.getMatterId(), type, by, byName, detail, Instant.now()));
    }

    private CaseDetail detail(LpLitigationCase c, String matterLabel, List<String> extraWarnings) {
        TenantId t = c.getTenantId();
        LocalDate today = today();
        List<LpCourtOrder> orders = orderRepo.findForCase(t, c.getId());
        List<String> warnings = new ArrayList<>(extraWarnings);
        for (LpCourtOrder o : orders) if (o.outstanding() && o.getComplianceDue().isBefore(today)) warnings.add("An order was due to be complied with by " + o.getComplianceDue() + " and has not been marked complied");
        return new CaseDetail(c.getId(), c.getMatterId(), matterLabel, c.getClientId(), c.getCourtLevel(), c.getCourtName(), c.getCaseNumber(), c.getOurRole(), c.getStage(), c.isOpen(),
                c.getOpposingParty(), c.getOpposingAttorney(), c.getOpposingAttorneyContact(), c.getJudge(), c.getIssuedOn(), c.getServedOn(), c.getOutcome(), c.getConcludedOn(),
                c.getOutcomeNote(), c.getNotes(), c.getCreatedByName(), c.getCreatedAt(), c.getUpdatedAt(),
                hearingRepo.findForCase(t, c.getId()).stream().map(h -> new HearingResponse(h.getId(), h.getHearingType(), h.getHearingDate(), h.getHearingTime(), h.getCourtRoom(),
                        h.getAppearing(), h.getStatus(), h.getPostponements(), h.getOutcome(), h.getCancelReason(), h.getKeyDateId())).toList(),
                orders.stream().map(o -> new OrderResponse(o.getId(), o.getHearingId(), o.getOrderDate(), o.getSummary(), o.getComplianceDue(), o.getCompliedOn(), o.getCompliedNote(),
                        o.outstanding(), o.getKeyDateId(), o.getCreatedByName())).toList(),
                eventRepo.findRecentForCase(t, c.getId(), PageRequest.of(0, EVENTS_SHOWN)).stream().map(e -> new EventResponse(e.getEventType(), e.getActorName(), e.getDetail(), e.getOccurredAt())).toList(),
                warnings);
    }
}

// security/application/internal/GuardComplaintService.java
package za.co.handyflow.platform.security.application.internal;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.*;
import za.co.handyflow.platform.security.domain.model.GuardComplaint;
import za.co.handyflow.platform.security.domain.model.GuardComplaintEvent;
import za.co.handyflow.platform.security.domain.repository.GuardComplaintEventRepository;
import za.co.handyflow.platform.security.domain.repository.GuardComplaintRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardComplaintDtos.*;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.EvidenceItem;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guard complaints: logging, investigation, finding, action and closure, with a timeline and evidence files.
 * Which step may follow which is decided by ComplaintWorkflow. The service records what people decide and never
 * changes the guard's employment status: suspensions and hearings are carried out by people, then recorded here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardComplaintService {

    static final String SOURCE_MODULE = "security";
    static final String ENTITY_TYPE = "GuardComplaint";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardComplaintRepository repository;
    private final GuardComplaintEventRepository eventRepository;
    private final GuardRepository guardRepository;
    private final SiteRepository siteRepository;
    private final GuardService guardService;
    private final EvidenceFacade evidenceFacade;
    private final TenantNumberingFacade numberingFacade;

    // ── Logging and editing ───────────────────────────────────────────────────

    @Transactional
    public ComplaintDetail log(TenantId tenantId, SaveComplaintRequest req, UUID by, String byName) {
        return log(tenantId, req, by, byName, LocalDate.now(SAST));
    }

    @Transactional
    ComplaintDetail log(TenantId tenantId, SaveComplaintRequest req, UUID by, String byName, LocalDate today) {
        guardService.getGuard(tenantId, req.guardId());
        checkSite(tenantId, req.siteId());
        var parsed = parse(req, today);
        String number = numberingFacade.next(tenantId, "GUARD_COMPLAINT", "CMP");
        GuardComplaint c = GuardComplaint.log(tenantId, number, req.guardId(), req.siteId(), req.occurredOn(), parsed.category,
                parsed.severity, req.description(), parsed.complainant, req.complainantName(), req.complainantContact(),
                req.witnesses(), by, byName);
        repository.save(c);
        event(c, "LOGGED", Status.RECEIVED, null, by, byName);
        log.info("[Security] Complaint logged {} guardId={} category={} severity={}", number, req.guardId(), parsed.category, parsed.severity);
        return detail(c);
    }

    @Transactional
    public ComplaintDetail update(TenantId tenantId, UUID id, SaveComplaintRequest req, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        if (!ComplaintWorkflow.isEditable(c.getStatus()))
            throw conflict("The details cannot be changed once a finding has been made");
        if (!c.getGuardId().equals(req.guardId()))
            throw bad("A complaint cannot be moved to a different guard", "GUARD_CHANGE_NOT_ALLOWED");
        checkSite(tenantId, req.siteId());
        var parsed = parse(req, LocalDate.now(SAST));
        c.edit(req.siteId(), req.occurredOn(), parsed.category, parsed.severity, req.description(), parsed.complainant,
                req.complainantName(), req.complainantContact(), req.witnesses());
        repository.save(c);
        event(c, "EDITED", null, "Details updated", by, byName);
        return detail(c);
    }

    // ── Workflow steps ────────────────────────────────────────────────────────

    @Transactional
    public ComplaintDetail start(TenantId tenantId, UUID id, StartInvestigationRequest req, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        requireStep(c, Step.START);
        c.startInvestigation(req == null ? null : req.investigator());
        repository.save(c);
        event(c, "INVESTIGATION_STARTED", Status.UNDER_INVESTIGATION,
                c.getInvestigatorName() == null ? null : "Investigator: " + c.getInvestigatorName(), by, byName);
        return detail(c);
    }

    @Transactional
    public ComplaintDetail finding(TenantId tenantId, UUID id, FindingRequest req, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        requireStep(c, Step.FINDING);
        Finding finding = parseEnum(Finding.class, req.finding(), "finding");
        if (req.note() == null || req.note().isBlank()) throw bad("Record what the investigation found", "FINDING_NOTE_REQUIRED");
        c.recordFinding(finding, req.note(), byName);
        repository.save(c);
        event(c, "FINDING_RECORDED", Status.FINDING_MADE, finding.name().toLowerCase().replace('_', ' ') + ": " + req.note().trim(), by, byName);
        return detail(c);
    }

    @Transactional
    public ComplaintDetail action(TenantId tenantId, UUID id, ActionRequest req, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        requireStep(c, Step.ACTION);
        Action action = parseEnum(Action.class, req.action(), "action");
        String problem = ComplaintWorkflow.validateAction(c.getFinding(), action, req.note());
        if (problem != null) throw bad(problem, "INVALID_ACTION");
        c.recordAction(action, req.note(), byName);
        repository.save(c);
        event(c, "ACTION_RECORDED", Status.ACTION_TAKEN, action.name().toLowerCase().replace('_', ' ')
                + (req.note() == null || req.note().isBlank() ? "" : ": " + req.note().trim()), by, byName);
        return detail(c);
    }

    @Transactional
    public ComplaintDetail close(TenantId tenantId, UUID id, CloseRequest req, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        requireStep(c, Step.CLOSE);
        if (req == null || req.resolutionNote() == null || req.resolutionNote().isBlank())
            throw bad("Say how the complaint was resolved", "RESOLUTION_REQUIRED");
        c.close(req.resolutionNote(), byName);
        repository.save(c);
        event(c, "CLOSED", Status.CLOSED, req.resolutionNote(), by, byName);
        return detail(c);
    }

    @Transactional
    public ComplaintDetail withdraw(TenantId tenantId, UUID id, WithdrawRequest req, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        requireStep(c, Step.WITHDRAW);
        if (req.reason() == null || req.reason().isBlank()) throw bad("Give a reason for withdrawing", "REASON_REQUIRED");
        c.withdraw(req.reason(), byName);
        repository.save(c);
        event(c, "WITHDRAWN", Status.WITHDRAWN, req.reason(), by, byName);
        return detail(c);
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ComplaintDetail get(TenantId tenantId, UUID id) { return detail(find(tenantId, id)); }

    @Transactional(readOnly = true)
    public Page<ComplaintSummary> list(TenantId tenantId, String status, String severity, String category, UUID guardId, Pageable pageable) {
        Specification<GuardComplaint> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tenantId"), tenantId));
            if (status != null && !status.isBlank()) {
                if ("OPEN".equalsIgnoreCase(status)) {
                    p.add(cb.not(root.get("status").in(Status.CLOSED, Status.WITHDRAWN)));
                } else {
                    p.add(cb.equal(root.get("status"), parseEnum(Status.class, status, "status")));
                }
            }
            if (severity != null && !severity.isBlank()) p.add(cb.equal(root.get("severity"), parseEnum(Severity.class, severity, "severity")));
            if (category != null && !category.isBlank()) p.add(cb.equal(root.get("category"), parseEnum(Category.class, category, "category")));
            if (guardId != null) p.add(cb.equal(root.get("guardId"), guardId));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<GuardComplaint> page = repository.findAll(spec, pageable);
        Map<UUID, String> guards = new HashMap<>(), sites = new HashMap<>();
        return page.map(c -> summary(c, guards, sites));
    }

    /** A guard's complaints, newest first, for the Guard 360 page. The guard is already known to belong to the tenant. */
    @Transactional(readOnly = true)
    public List<ComplaintSummary> forGuard(TenantId tenantId, UUID guardId) {
        Map<UUID, String> guards = new HashMap<>(), sites = new HashMap<>();
        return repository.findForGuard(tenantId, guardId).stream().map(c -> summary(c, guards, sites)).toList();
    }

    /** Counts for the Guard 360 header. */
    public static ComplaintCounts counts(List<ComplaintSummary> complaints, LocalDate today) {
        LocalDate since = today.minusDays(90);
        int open = (int) complaints.stream().filter(ComplaintSummary::open).count();
        int last90 = (int) complaints.stream().filter(c -> !c.occurredOn().isBefore(since) && !"WITHDRAWN".equals(c.status())).count();
        int sub = (int) complaints.stream().filter(c -> !c.occurredOn().isBefore(since) && "SUBSTANTIATED".equals(c.finding())).count();
        return new ComplaintCounts(open, last90, sub);
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @Transactional
    public EvidenceResponse attach(TenantId tenantId, UUID id, MultipartFile file, String label, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        if (!ComplaintWorkflow.isOpen(c.getStatus())) throw conflict("Evidence cannot be added to a closed complaint");
        if (file == null || file.isEmpty()) throw bad("Choose a file to attach", "MISSING_FILE");
        String type = label == null || label.isBlank() ? "Complaint evidence" : label.trim();
        EvidenceResponse saved = evidenceFacade.attach(tenantId, file, type, SOURCE_MODULE, ENTITY_TYPE, id, null, by, byName);
        event(c, "EVIDENCE_ADDED", null, saved.fileName(), by, byName);
        return saved;
    }

    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, UUID id, UUID evidenceId) {
        requireOwned(tenantId, id, evidenceId);
        return evidenceFacade.download(tenantId, evidenceId);
    }

    @Transactional
    public void removeFile(TenantId tenantId, UUID id, UUID evidenceId, UUID by, String byName) {
        GuardComplaint c = find(tenantId, id);
        if (!ComplaintWorkflow.isOpen(c.getStatus())) throw conflict("Evidence cannot be removed from a closed complaint");
        requireOwned(tenantId, id, evidenceId);
        evidenceFacade.detach(tenantId, evidenceId);
        event(c, "EVIDENCE_REMOVED", null, null, by, byName);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    GuardComplaint find(TenantId tenantId, UUID id) {
        return repository.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("GuardComplaint", id.toString()));
    }

    private void requireOwned(TenantId tenantId, UUID id, UUID evidenceId) {
        find(tenantId, id);
        boolean owned = evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id).stream().anyMatch(e -> e.id().equals(evidenceId));
        if (!owned) throw new ResourceNotFoundException("Evidence", evidenceId.toString());
    }

    private void requireStep(GuardComplaint c, Step step) {
        if (!ComplaintWorkflow.allowedSteps(c.getStatus(), c.getFinding()).contains(step))
            throw conflict("That step is not available while the complaint is " + c.getStatus().name().toLowerCase().replace('_', ' '));
    }

    private void event(GuardComplaint c, String type, Status to, String note, UUID by, String byName) {
        eventRepository.save(GuardComplaintEvent.of(c.getTenantId(), c.getId(), type, to == null ? null : to.name(), note, by, byName));
    }

    private void checkSite(TenantId tenantId, UUID siteId) {
        if (siteId != null) siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId.toString()));
    }

    private record Parsed(Category category, Severity severity, ComplainantType complainant) {}

    private Parsed parse(SaveComplaintRequest req, LocalDate today) {
        if (req.description() == null || req.description().isBlank()) throw bad("Describe the complaint", "DESCRIPTION_REQUIRED");
        if (req.occurredOn().isAfter(today)) throw bad("The date of the complaint cannot be in the future", "INVALID_DATE");
        return new Parsed(parseEnum(Category.class, req.category(), "category"), parseEnum(Severity.class, req.severity(), "severity"),
                parseEnum(ComplainantType.class, req.complainantType(), "complainant type"));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String what) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Unknown " + what + ": " + value, "INVALID_" + what.toUpperCase().replace(' ', '_'));
        }
    }

    private static HandyFlowException bad(String message, String code) { return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code); }

    private static HandyFlowException conflict(String message) { return new HandyFlowException(message, HttpStatus.CONFLICT, "COMPLAINT_STATE"); }

    private ComplaintSummary summary(GuardComplaint c, Map<UUID, String> guards, Map<UUID, String> sites) {
        String guardName = guards.computeIfAbsent(c.getGuardId(), k -> guardRepository.findById(k).map(g -> g.getFullName()).orElse(null));
        String siteName = c.getSiteId() == null ? null : sites.computeIfAbsent(c.getSiteId(), k -> siteRepository.findById(k).map(s -> s.getName()).orElse(null));
        return new ComplaintSummary(c.getId(), c.getComplaintNumber(), c.getGuardId(), guardName, c.getSiteId(), siteName,
                c.getOccurredOn(), c.getCategory().name(), c.getSeverity().name(), c.getStatus().name(),
                c.getFinding() == null ? null : c.getFinding().name(), c.getAction() == null ? null : c.getAction().name(),
                ComplaintWorkflow.isOpen(c.getStatus()), ComplaintWorkflow.isUrgent(c.getCategory(), c.getSeverity()), c.getCreatedAt());
    }

    private ComplaintDetail detail(GuardComplaint c) {
        var s = summary(c, new HashMap<>(), new HashMap<>());
        var events = eventRepository.findForComplaint(c.getTenantId(), c.getId()).stream()
                .map(e -> new EventItem(e.getId(), e.getEventType(), e.getToStatus(), e.getNote(), e.getByName(), e.getAt())).toList();
        List<EvidenceItem> files = evidenceFacade.listFor(c.getTenantId(), SOURCE_MODULE, ENTITY_TYPE, c.getId()).stream()
                .map(e -> new EvidenceItem(e.id(), e.fileName(), e.evidenceType(), e.fileSizeBytes(), e.uploadedByName(), e.createdAt())).toList();
        return new ComplaintDetail(s, c.getDescription(), c.getComplainantType().name(), c.getComplainantName(), c.getComplainantContact(),
                c.getWitnesses(), c.getInvestigatorName(), c.getFindingNote(), c.getFindingByName(), c.getFindingAt(),
                c.getActionNote(), c.getActionByName(), c.getActionAt(), c.getResolutionNote(), c.getClosedByName(), c.getClosedAt(),
                c.getWithdrawnReason(), c.getCreatedByName(), ComplaintWorkflow.isEditable(c.getStatus()),
                ComplaintWorkflow.allowedSteps(c.getStatus(), c.getFinding()).stream().map(Enum::name).toList(), events, files);
    }
}

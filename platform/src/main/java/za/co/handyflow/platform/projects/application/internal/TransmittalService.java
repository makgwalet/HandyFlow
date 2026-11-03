package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.application.internal.document.TransmittalRules;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittal;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalItem;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTransmittalItemRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTransmittalRepository;
import za.co.handyflow.platform.projects.dto.CreateTransmittalRequest;
import za.co.handyflow.platform.projects.dto.TransmittalRecipientRequest;
import za.co.handyflow.platform.projects.dto.TransmittalRecipientResponse;
import za.co.handyflow.platform.projects.dto.TransmittalResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Transmittal register: a permanent record of which document revisions went to whom, for what
 * purpose. Each recipient with an email address is given their OWN secure, expiring link by
 * TransmittalAccessService, which also emails it (links only, never attachments).
 *
 * Every document must belong to this project (and tenant) and be live (CURRENT, FOR_REVIEW or
 * APPROVED). Issued FOR_CONSTRUCTION or AS_BUILT, every document must be APPROVED (TransmittalRules).
 * If any are not, the WHOLE request is refused and the offenders are listed, so a transmittal never
 * silently goes out with a superseded drawing or a draft marked "for construction". Items snapshot
 * number, title, revision, status, file name and file hash at the time of sending.
 *
 * See TransmittalAccessService for how recipients prove who they are and what they may open.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransmittalService {

    private final ProjectRepository                projectRepo;
    private final ProjectDocumentRepository        docRepo;
    private final ProjectTransmittalRepository     transmittalRepo;
    private final ProjectTransmittalItemRepository itemRepo;
    private final SequenceService                  sequenceService;
    private final TransmittalAccessService         accessService;

    @Transactional
    public TransmittalResponse create(TenantId tenantId, UUID projectId, CreateTransmittalRequest req,
                                      UUID by, String byName) {
        Project project = verifyProject(tenantId, projectId);
        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new HandyFlowException("Project is CLOSED — no further transmittals can be recorded",
                    HttpStatus.CONFLICT, "PROJECT_CLOSED");
        }

        // De-duplicate while keeping the caller's order.
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(req.documentIds()));
        List<ProjectDocument> docs = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (UUID id : ids) {
            Optional<ProjectDocument> d = docRepo.findByTenantAndId(tenantId.getValue(), id)
                    .filter(x -> projectId.equals(x.getProjectId()));
            if (d.isEmpty()) {
                problems.add(id + " (not found in this project)");
            } else {
                String why = TransmittalRules.problem(req.purpose(), d.get().statusEnum());
                if (why != null) problems.add(label(d.get()) + " " + why);
                else docs.add(d.get());
            }
        }
        if (!problems.isEmpty()) {
            throw new HandyFlowException("These documents cannot be transmitted: " + String.join("; ", problems),
                    HttpStatus.CONFLICT, "DOCUMENTS_NOT_TRANSMITTABLE");
        }

        String number = "TR-" + String.format("%03d", sequenceService.next(tenantId.getValue(), "TRN:" + projectId));
        ProjectTransmittal t = transmittalRepo.save(ProjectTransmittal.create(
                tenantId.getValue(), projectId, number, req.subject().trim(), req.recipientName().trim(),
                blankToNull(req.recipientOrganisation()), blankToNull(req.recipientEmail()),
                req.purpose(), blankToNull(req.notes()), by, byName));
        List<ProjectTransmittalItem> items = itemRepo.saveAll(
                docs.stream().map(d -> ProjectTransmittalItem.snapshot(t.getId(), d)).toList());

        // Each recipient with an email address gets their OWN secure link; documents are never attached.
        List<TransmittalRecipientRequest> specs = new ArrayList<>();
        if (blankToNull(req.recipientEmail()) != null) {
            specs.add(new TransmittalRecipientRequest(req.recipientName().trim(), blankToNull(req.recipientOrganisation()),
                    req.recipientEmail().trim(), true, true, null));
        }
        if (req.additionalRecipients() != null) specs.addAll(req.additionalRecipients());
        List<TransmittalRecipientResponse> recipients =
                accessService.issue(tenantId, project, t, items, specs, req.expiryDays(),
                        Boolean.TRUE.equals(req.attachFiles()), by, byName);

        log.info("Transmittal {} to '{}' project={} documents={} recipients={}",
                number, req.recipientName(), projectId, items.size(), recipients.size());
        return TransmittalResponse.of(t, items, recipients);
    }

    @Transactional(readOnly = true)
    public List<TransmittalResponse> list(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        List<ProjectTransmittal> all = transmittalRepo.findByProject(tenantId.getValue(), projectId);
        return all.stream().map(t -> TransmittalResponse.of(t, itemRepo.findByTransmittal(t.getId()),
                accessService.recipientViews(tenantId, t.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public TransmittalResponse get(TenantId tenantId, UUID projectId, UUID id) {
        ProjectTransmittal t = find(tenantId, projectId, id);
        return TransmittalResponse.of(t, itemRepo.findByTransmittal(t.getId()), accessService.recipientViews(tenantId, t.getId()));
    }

    @Transactional
    public TransmittalResponse acknowledge(TenantId tenantId, UUID projectId, UUID id, String acknowledgedBy) {
        verifyProject(tenantId, projectId);
        // Locked, so two simultaneous acknowledgements cannot both succeed and overwrite each other.
        ProjectTransmittal t = transmittalRepo.findByProjectAndIdForUpdate(tenantId.getValue(), projectId, id)
                .orElseThrow(() -> new HandyFlowException("Transmittal not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        t.acknowledge(acknowledgedBy.trim());
        transmittalRepo.save(t);
        return TransmittalResponse.of(t, itemRepo.findByTransmittal(t.getId()), accessService.recipientViews(tenantId, t.getId()));
    }

    private ProjectTransmittal find(TenantId tenantId, UUID projectId, UUID id) {
        verifyProject(tenantId, projectId);
        return transmittalRepo.findByProjectAndId(tenantId.getValue(), projectId, id)
                .orElseThrow(() -> new HandyFlowException("Transmittal not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private Project verifyProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private static String label(ProjectDocument d) {
        return (d.getDocumentNumber() != null ? d.getDocumentNumber() : d.getTitle())
                + (d.getRevision() != null ? " rev " + d.getRevision() : "");
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
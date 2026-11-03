package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import za.co.handyflow.platform.projects.dto.TransmittalResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Transmittal register: a permanent record of which document revisions went to whom, for what
 * purpose. RECORD ONLY — nothing is emailed from here.
 *
 * Every document must belong to this project (and tenant) and be live (CURRENT, FOR_REVIEW or
 * APPROVED). If any are not, the WHOLE request is refused and the offenders are listed, so a
 * transmittal never silently goes out with a superseded drawing. Items snapshot number, title,
 * revision and status at the time of sending.
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
            } else if (!d.get().statusEnum().canBeTransmitted()) {
                problems.add(label(d.get()) + " is " + d.get().getStatus());
            } else {
                docs.add(d.get());
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

        log.info("Transmittal {} to '{}' project={} documents={}", number, req.recipientName(), projectId, items.size());
        return TransmittalResponse.of(t, items);
    }

    @Transactional(readOnly = true)
    public List<TransmittalResponse> list(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        List<ProjectTransmittal> all = transmittalRepo.findByProject(tenantId.getValue(), projectId);
        return all.stream().map(t -> TransmittalResponse.of(t, itemRepo.findByTransmittal(t.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public TransmittalResponse get(TenantId tenantId, UUID projectId, UUID id) {
        ProjectTransmittal t = find(tenantId, projectId, id);
        return TransmittalResponse.of(t, itemRepo.findByTransmittal(t.getId()));
    }

    @Transactional
    public TransmittalResponse acknowledge(TenantId tenantId, UUID projectId, UUID id, String acknowledgedBy) {
        ProjectTransmittal t = find(tenantId, projectId, id);
        t.acknowledge(acknowledgedBy.trim());
        transmittalRepo.save(t);
        return TransmittalResponse.of(t, itemRepo.findByTransmittal(t.getId()));
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
package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.hr.domain.model.ErEvidenceRules;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Files attached to an employee relations case. They are kept as restricted evidence, so only these methods can list or open them: not the
 * general evidence screens, and not an external auditor. Whoever may work the case may add and read; nobody works a case about themselves.
 * Adding and removing are noted on the case timeline, hidden from the employee. A file is never destroyed; removing one hides it.
 */
@Service
@RequiredArgsConstructor
public class ErEvidenceService {

    private final ErCaseSupport support;
    private final EvidenceFacade evidence;

    @Transactional(readOnly = true)
    public List<EvidenceResponse> list(TenantId tenantId, UUID caseId, Set<String> held, UUID userId) {
        HrErCase c = readable(tenantId, caseId, held, userId);
        return evidence.listRestricted(tenantId, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, c.getId());
    }

    @Transactional
    public EvidenceResponse add(TenantId tenantId, UUID caseId, String kind, MultipartFile file, Set<String> held, UUID userId, String userName) {
        ErCaseSupport.requireManage(held);
        HrErCase c = support.findToAct(tenantId, caseId, held, userId);
        int active = evidence.listRestricted(tenantId, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, c.getId()).size();
        String name = ErEvidenceRules.safeName(file == null ? null : file.getOriginalFilename());
        ErCaseSupport.fail(ErEvidenceRules.uploadProblem(c.getStatus(), kind, name, file == null ? 0 : file.getSize(), head(file), active));
        EvidenceResponse saved = evidence.attachRestricted(tenantId, new Named(file, name, ErEvidenceRules.contentType(name)), kind,
                ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, c.getId(), userId, userName);
        support.event(c, "EVIDENCE_ADDED", ErEvidenceRules.addedNote(kind, name), false, userId);
        return saved;
    }

    @Transactional
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, UUID caseId, UUID evidenceId, Set<String> held, UUID userId) {
        HrErCase c = readable(tenantId, caseId, held, userId);
        return evidence.downloadRestricted(tenantId, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, c.getId(), evidenceId);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID caseId, UUID evidenceId, String reason, Set<String> held, UUID userId) {
        ErCaseSupport.requireManage(held);
        HrErCase c = support.findToAct(tenantId, caseId, held, userId);
        ErCaseSupport.fail(ErEvidenceRules.removeProblem(c.getStatus(), reason));
        EvidenceResponse file = evidence.listRestricted(tenantId, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, c.getId()).stream()
                .filter(e -> e.id().equals(evidenceId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", String.valueOf(evidenceId)));
        evidence.detachRestricted(tenantId, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, c.getId(), evidenceId);
        support.event(c, "EVIDENCE_REMOVED", ErEvidenceRules.removedNote(file.fileName(), reason), false, userId);
    }

    private HrErCase readable(TenantId tenantId, UUID caseId, Set<String> held, UUID userId) {
        return support.findToAct(tenantId, caseId, held, userId);
    }

    private static byte[] head(MultipartFile file) {
        if (file == null || file.isEmpty()) return new byte[0];
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(ErEvidenceRules.HEAD_BYTES);
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read.");
        }
    }

    /** The upload with the cleaned name and the type decided by the extension. */
    private record Named(MultipartFile inner, String name, String type) implements MultipartFile {
        @Override public String getName() { return inner.getName(); }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return type; }
        @Override public boolean isEmpty() { return inner.isEmpty(); }
        @Override public long getSize() { return inner.getSize(); }
        @Override public byte[] getBytes() throws IOException { return inner.getBytes(); }
        @Override public InputStream getInputStream() throws IOException { return inner.getInputStream(); }
        @Override public void transferTo(java.io.File dest) throws IOException { inner.transferTo(dest); }
    }
}

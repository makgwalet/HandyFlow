package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceEvidence;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceEvidenceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceItemRepository;
import za.co.handyflow.platform.fuel.dto.ComplianceLinkageDtos.EvidenceView;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.StoredFile;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.Upload;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.UUID;

/**
 * F01 leftover: a scan or photo of a licence or certificate, kept in the shared file store. The same checks as delivery evidence:
 * the type is decided from the bytes (JPEG, PNG, WebP or PDF) and the size is capped. The record stays append-only; verification
 * and replacement are in {@link FuelComplianceService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelComplianceEvidenceFileService {

    private final FuelComplianceItemRepository items;
    private final FuelComplianceEvidenceRepository evidence;
    private final FileStorageService storage;

    @Transactional
    public EvidenceView add(TenantId tenantId, UUID itemId, String filename, byte[] bytes, String note, UUID by) {
        items.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("Compliance item", itemId.toString()));
        String type = FuelEvidenceFileService.validate(new Upload("DOCUMENT", filename, bytes, note, null));
        String name = FuelEvidenceFileService.safeName(filename, type);
        String key;
        try {
            key = storage.store("fuel/compliance/" + tenantId + "/" + itemId, name, type, bytes);
        } catch (IOException ex) {
            log.warn("Could not store compliance evidence for item {}", itemId, ex);
            throw new IllegalStateException("The file could not be saved. Try again.");
        }
        try {
            return FuelComplianceService.view(evidence.save(FuelComplianceEvidence.createFile(tenantId, itemId, key, type, bytes.length, name, note, by)));
        } catch (RuntimeException ex) {
            try { storage.delete(key); } catch (IOException | RuntimeException ignored) { log.warn("Could not remove orphaned compliance file {}", key); }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public StoredFile download(TenantId tenantId, UUID evidenceId) {
        FuelComplianceEvidence e = evidence.findOne(tenantId, evidenceId).orElseThrow(() -> new ResourceNotFoundException("Evidence", evidenceId.toString()));
        if (!e.isFileStored()) throw new ResourceNotFoundException("Evidence file", evidenceId.toString());
        try {
            return new StoredFile(storage.retrieve(e.getFileRef()), e.getContentType(), e.getFileName() == null ? "evidence" : e.getFileName());
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read compliance file for {}", evidenceId, ex);
            throw new IllegalStateException("The stored file could not be read.");
        }
    }
}

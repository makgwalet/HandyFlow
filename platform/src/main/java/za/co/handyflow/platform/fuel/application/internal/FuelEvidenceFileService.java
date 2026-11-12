package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryEvidence;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryEvidenceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.StoredFile;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.Upload;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.EvidenceView;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Photo and signature capture (C05 leftover). Files go to the shared file store; the evidence record keeps the store's key and
 * a description of the file. The file type is decided from the bytes, never from what the phone says it is, and only JPEG, PNG
 * and WebP images (and PDF for documents) are accepted. Records stay append-only: see {@link FuelEvidenceService} for replace and verify.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelEvidenceFileService {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> FILE_KINDS = Set.of("PHOTO", "SIGNATURE", "DOCUMENT");
    private static final Set<String> CAPTURABLE = Set.of("IN_TRANSIT", "DELIVERED");

    private final FuelDeliveryRepository deliveries;
    private final FuelDeliveryEvidenceRepository evidence;
    private final HrFacade hr;
    private final FileStorageService storage;

    @Transactional
    public EvidenceView addAsOffice(TenantId tenantId, UUID deliveryId, Upload up, UUID by) {
        return add(tenantId, delivery(tenantId, deliveryId), up, by);
    }

    /** A driver may attach files only to their own load; another driver's load looks like a missing one. */
    @Transactional
    public EvidenceView addAsDriver(TenantId tenantId, UUID userId, UUID deliveryId, Upload up) {
        FuelDelivery d = delivery(tenantId, deliveryId);
        requireOwnLoad(tenantId, userId, d);
        return add(tenantId, d, up, userId);
    }

    @Transactional(readOnly = true)
    public StoredFile download(TenantId tenantId, UUID evidenceId) {
        return read(found(tenantId, evidenceId));
    }

    @Transactional(readOnly = true)
    public StoredFile downloadAsDriver(TenantId tenantId, UUID userId, UUID evidenceId) {
        FuelDeliveryEvidence e = found(tenantId, evidenceId);
        requireOwnLoad(tenantId, userId, delivery(tenantId, e.getDeliveryId()));
        return read(e);
    }

    private EvidenceView add(TenantId tenantId, FuelDelivery d, Upload up, UUID by) {
        if (!CAPTURABLE.contains(d.getStatus())) {
            throw new IllegalStateException("Evidence can be added once a delivery is on the road or delivered. This one is " + d.getStatus().toLowerCase().replace('_', ' ') + ".");
        }
        // A replay of an upload the server already saved returns that record; checked first so no second file is stored.
        if (up.clientRequestId() != null) {
            var existing = evidence.findByClientRequest(tenantId, d.getId(), up.clientRequestId());
            if (existing.isPresent()) {
                log.info("Evidence upload {} for delivery {} already saved; returning the first record", up.clientRequestId(), d.getId());
                return FuelEvidenceService.view(existing.get());
            }
        }
        String type = validate(up);
        String key;
        try {
            key = storage.store("fuel/evidence/" + tenantId + "/" + d.getId(), safeName(up.filename(), type), type, up.bytes());
        } catch (IOException ex) {
            log.warn("Could not store evidence file for delivery {}", d.getId(), ex);
            throw new IllegalStateException("The file could not be saved. Try again.");
        }
        try {
            FuelDeliveryEvidence e = FuelDeliveryEvidence.createFile(tenantId, d.getId(), up.kind(), key, type, up.bytes().length, safeName(up.filename(), type), up.note(), by)
                    .withClientRequestId(up.clientRequestId());
            return FuelEvidenceService.view(evidence.save(e));
        } catch (RuntimeException ex) {
            try { storage.delete(key); } catch (IOException | RuntimeException ignored) { log.warn("Could not remove orphaned evidence file {}", key); }
            throw ex;
        }
    }

    private StoredFile read(FuelDeliveryEvidence e) {
        if (!e.isFileStored()) throw new ResourceNotFoundException("Evidence file", e.getId().toString());
        try {
            return new StoredFile(storage.retrieve(e.getFileRef()), e.getContentType(), e.getFileName() == null ? "evidence" : e.getFileName());
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not read evidence file for {}", e.getId(), ex);
            throw new IllegalStateException("The stored file could not be read.");
        }
    }

    /** Returns the file's real type, or refuses the upload. */
    static String validate(Upload up) {
        if (up == null || up.kind() == null || !FILE_KINDS.contains(up.kind())) throw new IllegalArgumentException("A file must be a PHOTO, SIGNATURE or DOCUMENT.");
        byte[] b = up.bytes();
        if (b == null || b.length == 0) throw new IllegalArgumentException("The file is empty.");
        if (b.length > MAX_BYTES) throw new IllegalArgumentException("The file is too large. The limit is 5 MB.");
        String type = sniff(b);
        if (type == null || ("application/pdf".equals(type) && !"DOCUMENT".equals(up.kind()))) {
            throw new IllegalArgumentException("Attach a JPEG, PNG or WebP image" + ("DOCUMENT".equals(up.kind()) ? " or a PDF." : "."));
        }
        return type;
    }

    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        return null;
    }

    /** A readable name for the record: no folders, no odd characters, and the extension that matches the real type. */
    static String safeName(String original, String type) {
        String ext = switch (type) { case "image/jpeg" -> "jpg"; case "image/png" -> "png"; case "image/webp" -> "webp"; default -> "pdf"; };
        String base = original == null ? "" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.length() > 60) base = base.substring(0, 60);
        if (base.isEmpty()) base = "evidence";
        return base + "." + ext;
    }

    private void requireOwnLoad(TenantId tenantId, UUID userId, FuelDelivery d) {
        if (userId == null) throw new IllegalStateException("You are not signed in as a user.");
        EmployeeSummary me = hr.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
        if (!me.id().equals(d.getDriverEmployeeId())) throw new ResourceNotFoundException("Delivery", d.getId().toString());
    }

    private FuelDelivery delivery(TenantId tenantId, UUID id) {
        return deliveries.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Delivery", id.toString()));
    }

    private FuelDeliveryEvidence found(TenantId tenantId, UUID id) {
        return evidence.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Evidence", id.toString()));
    }
}

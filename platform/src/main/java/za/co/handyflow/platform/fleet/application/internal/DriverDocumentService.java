package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.fleet.domain.model.DriverDocument;
import za.co.handyflow.platform.fleet.domain.model.DriverDocumentType;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentStatus;
import za.co.handyflow.platform.fleet.domain.repository.DriverDocumentRepository;
import za.co.handyflow.platform.fleet.domain.repository.DriverRepository;
import za.co.handyflow.platform.fleet.dto.DriverDocumentResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A driver's file: upload (singleton types supersede the old version), list, download and soft-delete. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverDocumentService {

    static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository documentRepository;
    private final FleetUploadSupport uploads;

    public record DocumentDownload(String fileName, String contentType, long sizeBytes, InputStream stream) {}

    @Transactional
    public DriverDocumentResponse upload(TenantId tenantId, UUID driverId, UUID uploadedBy, MultipartFile file,
                                         DriverDocumentType type, String title, String documentNumber,
                                         LocalDate issueDate, LocalDate expiryDate, String issuer, String notes) {
        requireDriver(tenantId, driverId);
        String contentType = uploads.validate(file, ALLOWED_TYPES, MAX_FILE_BYTES);
        String cleanTitle = blankToNull(title);
        DriverDocumentRules.check(type, contentType, cleanTitle, issueDate, expiryDate);

        String key = uploads.store("fleet/" + tenantId.getValue() + "/drivers/" + driverId + "/documents", file, contentType);

        int version = documentRepository.findMaxVersion(tenantId, driverId, type) + 1;
        if (type.isSingleton()) {
            documentRepository.findByDriverTypeAndStatus(tenantId, driverId, type, VehicleDocumentStatus.CURRENT)
                    .forEach(old -> {
                        old.supersede();
                        documentRepository.save(old);
                    });
            // Flush so the superseding UPDATE reaches the DB before the new CURRENT row is inserted,
            // otherwise the partial unique index sees two CURRENT rows.
            documentRepository.flush();
        }

        DriverDocument doc = DriverDocument.create(tenantId, driverId, type, cleanTitle,
                blankToNull(documentNumber), issueDate, expiryDate, blankToNull(issuer), blankToNull(notes), version,
                uploads.sanitiseFileName(file.getOriginalFilename()), contentType, file.getSize(), key, uploadedBy);
        documentRepository.save(doc);
        log.info("Driver document uploaded driver={} type={} version={} tenant={}", driverId, type, version, tenantId);
        return toResponse(doc);
    }

    @Transactional(readOnly = true)
    public List<DriverDocumentResponse> list(TenantId tenantId, UUID driverId, DriverDocumentType type, boolean includeSuperseded) {
        requireDriver(tenantId, driverId);
        List<DriverDocument> docs = includeSuperseded
                ? documentRepository.findAllByDriver(tenantId, driverId)
                : documentRepository.findByDriverAndStatus(tenantId, driverId, VehicleDocumentStatus.CURRENT);
        return docs.stream().filter(d -> type == null || d.getType() == type).map(this::toResponse).toList();
    }

    /** The caller owns closing the stream. Tenant and driver scoping is enforced by the lookup. */
    @Transactional(readOnly = true)
    public DocumentDownload download(TenantId tenantId, UUID driverId, UUID documentId) {
        DriverDocument doc = find(tenantId, driverId, documentId);
        return new DocumentDownload(doc.getFileName(), doc.getContentType(), doc.getSizeBytes(), uploads.open(doc.getStorageKey()));
    }

    /** Soft delete only: the row is hidden and the stored file is kept as evidence. */
    @Transactional
    public void delete(TenantId tenantId, UUID driverId, UUID documentId, UUID deletedBy) {
        DriverDocument doc = find(tenantId, driverId, documentId);
        doc.softDelete(deletedBy);
        documentRepository.save(doc);
        log.info("Driver document deleted id={} driver={} by={}", documentId, driverId, deletedBy);
    }

    private void requireDriver(TenantId tenantId, UUID driverId) {
        driverRepository.findActiveById(tenantId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId.toString()));
    }

    private DriverDocument find(TenantId tenantId, UUID driverId, UUID documentId) {
        return documentRepository.findActiveById(tenantId, driverId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("DriverDocument", documentId.toString()));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private DriverDocumentResponse toResponse(DriverDocument d) {
        Long days = d.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), d.getExpiryDate());
        return new DriverDocumentResponse(d.getId(), d.getDriverId(), d.getType().name(), d.getTitle(),
                d.getDocumentNumber(), d.getIssueDate(), d.getExpiryDate(), d.getIssuer(), d.getNotes(),
                d.getDocumentVersion(), d.getStatus().name(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getUploadedBy(), d.getUploadedAt(), days != null && days < 0, days);
    }
}

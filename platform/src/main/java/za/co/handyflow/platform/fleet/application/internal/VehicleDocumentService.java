package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocument;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentType;
import za.co.handyflow.platform.fleet.domain.repository.VehicleDocumentRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.VehicleDocumentResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vehicle document vault: upload (with versioning for singleton types), list, download,
 * soft-delete. Documents of a type that tracks a vehicle expiry (licence disc,
 * roadworthy, insurance) write their expiry through to {@link Vehicle}, so the existing
 * scheduler's alerts fire off vault data with no second source of truth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleDocumentService {

    static final long MAX_FILE_BYTES = 15L * 1024 * 1024;

    /** Allowed content types. Each is also verified against the file's leading bytes — the client-declared type alone is not trusted. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "application/pdf", "PDF",
            "image/jpeg", "JPEG",
            "image/png", "PNG",
            "image/webp", "WEBP",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "ZIP");

    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final FleetUploadSupport uploads;

    public record DocumentDownload(String fileName, String contentType, long sizeBytes, InputStream stream) {}

    // ── Upload ───────────────────────────────────────────────────────────

    @Transactional
    public VehicleDocumentResponse upload(TenantId tenantId, UUID vehicleId, UUID uploadedBy,
                                          MultipartFile file, VehicleDocumentType type,
                                          String documentNumber, LocalDate issueDate, LocalDate expiryDate,
                                          String issuer, String notes) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);

        String contentType = validateFile(file);
        if (type == VehicleDocumentType.PHOTO && !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("A vehicle photo must be a JPEG, PNG or WebP image");
        }
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before the issue date");
        }
        if (type.tracksVehicleExpiry() && expiryDate == null) {
            throw new IllegalArgumentException("An expiry date is required for " + type + " documents");
        }

        String key = uploads.store("fleet/" + tenantId.getValue() + "/" + vehicleId + "/documents", file, contentType);

        int version = documentRepository.findMaxVersion(tenantId, vehicleId, type) + 1;
        if (type.isSingleton()) {
            documentRepository.findByVehicleTypeAndStatus(tenantId, vehicleId, type, VehicleDocumentStatus.CURRENT)
                    .forEach(old -> {
                        old.supersede();
                        documentRepository.save(old);
                    });
            // Flush so the superseding UPDATE reaches the DB before the new CURRENT row is
            // inserted — otherwise the partial unique index sees two CURRENT rows.
            documentRepository.flush();
        }

        VehicleDocument doc = VehicleDocument.create(
                tenantId, vehicleId, type,
                blankToNull(documentNumber), issueDate, expiryDate,
                blankToNull(issuer), blankToNull(notes), version,
                sanitiseFileName(file.getOriginalFilename()), contentType, file.getSize(),
                key, uploadedBy);
        documentRepository.save(doc);

        if (type.tracksVehicleExpiry()) {
            vehicle.syncComplianceExpiry(type, expiryDate);
            vehicleRepository.save(vehicle);
        }

        log.info("Vehicle document uploaded vehicle={} type={} version={} tenant={}",
                vehicleId, type, version, tenantId);
        return toResponse(doc);
    }

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VehicleDocumentResponse> list(TenantId tenantId, UUID vehicleId,
                                              VehicleDocumentType type, boolean includeSuperseded) {
        findVehicle(tenantId, vehicleId);
        List<VehicleDocument> docs = includeSuperseded
                ? documentRepository.findAllByVehicle(tenantId, vehicleId)
                : documentRepository.findByVehicleAndStatus(tenantId, vehicleId, VehicleDocumentStatus.CURRENT);
        return docs.stream()
                .filter(d -> type == null || d.getType() == type)
                .map(this::toResponse)
                .toList();
    }

    /** The caller owns closing {@code stream}. Tenant and vehicle scoping is enforced by the lookup. */
    @Transactional(readOnly = true)
    public DocumentDownload download(TenantId tenantId, UUID vehicleId, UUID documentId) {
        VehicleDocument doc = findDocument(tenantId, vehicleId, documentId);
        return new DocumentDownload(doc.getFileName(), doc.getContentType(),
                doc.getSizeBytes(), uploads.open(doc.getStorageKey()));
    }

    // ── Delete ───────────────────────────────────────────────────────────

    /**
     * Soft delete only — the row is hidden and the stored file is retained as evidence.
     * Deleting the current singleton document does NOT promote an older version or
     * touch the vehicle's expiry date; upload a replacement to change either.
     */
    @Transactional
    public void delete(TenantId tenantId, UUID vehicleId, UUID documentId, UUID deletedBy) {
        VehicleDocument doc = findDocument(tenantId, vehicleId, documentId);
        doc.softDelete(deletedBy);
        documentRepository.save(doc);
        log.info("Vehicle document deleted id={} vehicle={} by={}", documentId, vehicleId, deletedBy);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Vehicle findVehicle(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId.toString()));
    }

    private VehicleDocument findDocument(TenantId tenantId, UUID vehicleId, UUID documentId) {
        return documentRepository.findActiveById(tenantId, vehicleId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("VehicleDocument", documentId.toString()));
    }

    /** Returns the normalised content type, or throws IllegalArgumentException. */
    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "File is too large (max " + (MAX_FILE_BYTES / 1024 / 1024) + " MB)");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase().trim();
        String signature = ALLOWED_TYPES.get(contentType);
        if (signature == null) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed: PDF, JPEG, PNG, WebP, DOCX");
        }
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
        if (!matchesSignature(signature, head, read)) {
            throw new IllegalArgumentException("File content does not match its declared type (" + contentType + ")");
        }
        return contentType;
    }

    private boolean matchesSignature(String signature, byte[] h, int len) {
        if (len < 4) return false;
        return switch (signature) {
            case "PDF"  -> h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case "JPEG" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "PNG"  -> (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "WEBP" -> len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            case "ZIP"  -> h[0] == 'P' && h[1] == 'K';
            default     -> false;
        };
    }

    /** Keeps only safe characters; the stored object key never uses this name, it is for display / download only. */
    private String sanitiseFileName(String original) {
        if (original == null || original.isBlank()) return "document";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name.isEmpty() ? "document" : name;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private VehicleDocumentResponse toResponse(VehicleDocument d) {
        Long days = d.getExpiryDate() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), d.getExpiryDate());
        return new VehicleDocumentResponse(
                d.getId(), d.getVehicleId(), d.getType().name(),
                d.getDocumentNumber(), d.getIssueDate(), d.getExpiryDate(),
                d.getIssuer(), d.getNotes(), d.getDocumentVersion(), d.getStatus().name(),
                d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getUploadedBy(), d.getUploadedAt(),
                days != null && days < 0, days
        );
    }
}
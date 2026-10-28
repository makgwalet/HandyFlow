package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFile;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditWorkpaperFileRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** Gives back the file that was uploaded as a workpaper, and records who took it. */
@Service
@RequiredArgsConstructor
public class AuditWorkpaperDownloadService {
    public record Download(String fileName, String mimeType, byte[] content) {}

    private final AuditWorkpaperFileRepository files;
    private final AuditTrailFacade trail;

    @Transactional
    public Download download(TenantId tenant, UUID fileId) {
        AuditWorkpaperFile f = files.findByTenantAndId(tenant.getValue(), fileId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> new HandyFlowException("Workpaper not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (f.getFileContentBase64() == null || f.getFileContentBase64().isBlank())
            throw new HandyFlowException("This workpaper has no stored file", HttpStatus.NOT_FOUND, "NO_CONTENT");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(f.getFileContentBase64()); }
        catch (IllegalArgumentException e) { throw new HandyFlowException("The stored file is damaged", HttpStatus.INTERNAL_SERVER_ERROR, "CORRUPT_FILE"); }
        trail.record(tenant, "AUDIT_WORKPAPER_DOWNLOADED", "WORKPAPER", f.getId(), null, Map.of("fileName", f.getFileName(), "version", f.getVersionNumber()));
        return new Download(DownloadNames.safe(f.getFileName()), f.getMimeType() == null ? "application/octet-stream" : f.getMimeType(), bytes);
    }
}

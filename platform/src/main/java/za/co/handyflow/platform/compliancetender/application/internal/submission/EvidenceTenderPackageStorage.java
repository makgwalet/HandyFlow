package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade.DownloadedEvidence;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * V1 storage: packages are kept as Evidence, like every other document in this module.
 * <p>
 * KNOWN LIMIT, found by reading {@code EvidenceService}: Evidence refuses any single file over 20 MB, so
 * a package larger than that cannot be stored through this implementation and the store call fails with
 * Evidence's own "File is too large" error. That is not a business limit of the builder (ADR-005
 * decision 5); it is why storage sits behind {@link TenderPackageStorage}. Large packages need a storage
 * implementation without that ceiling, and this class does not hide the problem by splitting or trimming.
 */
@Component
@RequiredArgsConstructor
public class EvidenceTenderPackageStorage implements TenderPackageStorage {

    static final String SOURCE_MODULE = "compliancetender";
    static final String EVIDENCE_TYPE = "TENDER_PACKAGE";
    static final String ENTITY_TYPE = "TenderPackage";

    private final EvidenceFacade evidenceFacade;

    @Override
    public Stored store(TenantId tenantId, UUID packageId, String fileName, String contentType, byte[] content,
                        UUID storedBy, String storedByName) {
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, new BytesFile(fileName, contentType, content),
                EVIDENCE_TYPE, SOURCE_MODULE, ENTITY_TYPE, packageId, null, storedBy, storedByName);
        return new Stored(evidence.id(), evidence.fileName(), evidence.fileSizeBytes(), PackageManifest.sha256Hex(content));
    }

    @Override
    public Loaded load(TenantId tenantId, UUID storageId) {
        DownloadedEvidence downloaded = evidenceFacade.download(tenantId, storageId);
        return new Loaded(downloaded.content(), downloaded.fileName(), downloaded.contentType());
    }

    /** A MultipartFile over bytes already in memory; Evidence's contract takes a MultipartFile. */
    private record BytesFile(String name, String contentType, byte[] content) implements MultipartFile {

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), content); }
        @Override public void transferTo(Path dest) throws IOException { Files.write(dest, content); }
    }
}

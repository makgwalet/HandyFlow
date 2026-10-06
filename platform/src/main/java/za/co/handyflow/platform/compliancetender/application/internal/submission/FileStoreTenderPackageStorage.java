package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Package storage over the platform's existing {@link FileStorageService} port, so which backend holds the
 * bytes is already a configuration choice ({@code file-storage.provider}: the database by default, local
 * disk, and an object store when one is wired up) and no second storage mechanism is added. Packages are
 * kept apart from Evidence on purpose: a package is an assembled artifact, not evidence, and Evidence
 * refuses files over 20 MB (ADR-005 decision 4).
 * <p>
 * For packages of any real size set {@code file-storage.provider=local} (or an object store): the
 * default database backend keeps the bytes in a BYTEA column, which the port's own documentation says
 * should not carry large files. The port takes whole byte arrays, so the system ceiling on package size
 * (see {@link SubmissionProfileResolver}) is also a memory limit.
 */
@Component
@RequiredArgsConstructor
public class FileStoreTenderPackageStorage implements TenderPackageStorage {

    private final FileStorageService files;

    @Override
    public Stored store(TenantId tenantId, UUID tenderId, UUID packageId, String fileName, String contentType, byte[] content) {
        String prefix = "tender-packages/" + tenantId.getValue() + "/" + tenderId + "/" + packageId;
        try {
            String key = files.store(prefix, fileName, contentType, content);
            return new Stored(key, content.length, PackageManifest.sha256Hex(content));
        } catch (IOException e) {
            throw new UncheckedIOException("The package could not be stored.", e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return files.retrieve(storageKey);
        } catch (IOException e) {
            throw new UncheckedIOException("The package could not be read.", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            files.delete(storageKey);
        } catch (IOException e) {
            throw new UncheckedIOException("The package could not be deleted.", e);
        }
    }
}

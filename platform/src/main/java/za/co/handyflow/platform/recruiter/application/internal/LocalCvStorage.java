package za.co.handyflow.platform.recruiter.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Dev-phase CvStorage implementation — writes to local disk instead of an
 * object store. Active by default (matchIfMissing = true) since there is
 * no S3 or equivalent infrastructure yet. Swap to a real implementation
 * later per CvStorage's own class Javadoc — this class shouldn't need to
 * change, just stop being the active @ConditionalOnProperty match.
 * <p>
 * IMPORTANT, NOT AN OVERSIGHT — FLAGGING LOUDLY: local disk storage does
 * NOT survive a redeploy on most hosting setups (ephemeral filesystems),
 * and does not work at all across multiple app instances behind a load
 * balancer — each instance only sees files written to its own disk. Fine
 * for single-instance local/dev use. Genuinely not fine for production.
 * Do not treat this as "storage is solved" when production deployment
 * planning happens — it isn't, yet.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "recruiter.cv-storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalCvStorage implements CvStorage {

    private static final String PREFIX = "local://";

    private final Path storageDir;

    public LocalCvStorage(@Value("${recruiter.cv-storage.local-path:./data/cv-storage}") String localPath) {
        this.storageDir = Path.of(localPath);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create CV storage directory: " + localPath, e);
        }
        log.info("LocalCvStorage active — storing CVs under {}", storageDir.toAbsolutePath());
    }

    @Override
    public String store(byte[] bytes, String fileName) {
        // Always .pdf — CVs are documented as PDF-only end to end
        // (SubmitApplicationRequest.cvBase64's own comment, and the
        // frontend's file picker only accepts application/pdf). The
        // original fileName is intentionally not used to build the path —
        // nothing user-controlled goes into the on-disk path, which rules
        // out path-traversal via a crafted filename entirely.
        String storedName = UUID.randomUUID() + ".pdf";
        try {
            Files.write(storageDir.resolve(storedName), bytes);
        } catch (IOException e) {
            log.error("Failed to write CV to local storage: {}", e.getMessage(), e);
            throw new HandyFlowException("Failed to store CV",
                    HttpStatus.INTERNAL_SERVER_ERROR, "CV_STORE_FAILED");
        }
        return PREFIX + storedName;
    }

    @Override
    public byte[] retrieve(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a local storage reference: " + reference);
        }
        String storedName = reference.substring(PREFIX.length());
        try {
            return Files.readAllBytes(storageDir.resolve(storedName));
        } catch (IOException e) {
            log.error("Failed to read CV from local storage, reference={}: {}", reference, e.getMessage());
            throw new HandyFlowException("Stored CV could not be read",
                    HttpStatus.INTERNAL_SERVER_ERROR, "CV_READ_FAILED");
        }
    }
}
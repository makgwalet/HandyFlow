package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.fleet.application.storage.FleetFileStorage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Shared upload handling for Fleet features: type + size + magic-byte validation,
 * display-name sanitising, and storing a file with automatic clean-up if the
 * surrounding transaction rolls back. (VehicleDocumentService from the documents chunk
 * has its own private copy of the same checks and can be moved onto this helper later.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetUploadSupport {

    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> SIGNATURE_TYPES = Set.of("image/png", "image/jpeg");

    private final FleetFileStorage storage;

    /** Returns the normalised content type, or throws IllegalArgumentException. */
    public String validate(MultipartFile file, Set<String> allowedTypes, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File is too large (max " + (maxBytes / 1024 / 1024) + " MB)");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase().trim();
        if (!allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: " + String.join(", ", allowedTypes));
        }
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
        if (!matchesSignature(contentType, head, read)) {
            throw new IllegalArgumentException("File content does not match its declared type (" + contentType + ")");
        }
        return contentType;
    }

    /** Stores the file and registers removal of it should the current transaction not commit. */
    public void store(String key, MultipartFile file, String contentType) {
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try {
                            storage.delete(key);
                        } catch (IOException e) {
                            log.warn("Could not clean up orphaned fleet file key={}", key, e);
                        }
                    }
                }
            });
        }
    }

    public InputStream open(String key) {
        try {
            return storage.open(key);
        } catch (IOException e) {
            throw new IllegalStateException("Stored file could not be read", e);
        }
    }

    /** Safe display name only — never used to build a storage path. */
    public String sanitiseFileName(String original) {
        if (original == null || original.isBlank()) return "file";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name.isEmpty() ? "file" : name;
    }

    private boolean matchesSignature(String contentType, byte[] h, int len) {
        if (len < 4) return false;
        return switch (contentType) {
            case "application/pdf" -> h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case "image/jpeg" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "image/png" -> (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "image/webp" -> len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
    }
}
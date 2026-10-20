package za.co.handyflow.platform.documents.application.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.documents.application.DocumentScanner;

/** Used until a scanner is chosen: it scans nothing and says so, so the version is recorded as SKIPPED rather than as clean. */
@Component
@ConditionalOnProperty(name = "documents.scan.provider", havingValue = "none", matchIfMissing = true)
class NoScanDocumentScanner implements DocumentScanner {
    @Override
    public Result scan(String fileName, String contentType, byte[] content) { return Result.skipped("No malware scanner is configured"); }
}

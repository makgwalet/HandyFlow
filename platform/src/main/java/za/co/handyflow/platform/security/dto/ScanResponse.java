package za.co.handyflow.platform.security.dto;
import java.time.Instant; import java.util.UUID;
public record ScanResponse(
        UUID logId, String checkpointName,
        String siteName, Instant scannedAt
) {}
// security/dto/QrPayloadResponse.java
package za.co.handyflow.platform.security.dto;

import java.util.UUID;

/**
 * The signed QR payload for a checkpoint -- what should actually be encoded
 * into the printed/displayed QR image once a site has (or is about to)
 * enable requireSignedQr. payload is the full "{checkpointId}:{siteId}:{sig}"
 * string CheckpointScanService.verifyQrHmac() expects to receive back.
 */
public record QrPayloadResponse(
        UUID   checkpointId,
        String checkpointName,
        String payload
) {}
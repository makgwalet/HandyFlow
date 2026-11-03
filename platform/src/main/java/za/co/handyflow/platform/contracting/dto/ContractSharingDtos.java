package za.co.handyflow.platform.contracting.dto;

import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Secure sharing of a signed contract: requests and what the contract screen reads. */
public final class ContractSharingDtos {
    private ContractSharingDtos() {}

    /** {@code attach}: also email the PDF (only when attachments are switched on for the platform). */
    public record RecipientRequest(String name, String organisation, String email, Boolean canDownload, Boolean canAcknowledge,
                                   Integer expiryDays, Boolean attach) {}

    public record ReissueRequest(Integer expiryDays) {}

    public record RevokeRequest(String reason) {}

    public record ExecutedCopy(String sha256, Long sizeBytes, Instant storedAt) {}

    /** {@code shareable}: signed, not disposed. {@code executedCopy} null until the copy is stored (it is made on first share at the latest). */
    public record SharingView(UUID contractId, String contractNumber, String status, boolean shareable, boolean disposed,
                              ExecutedCopy executedCopy, UUID shareId, List<RecipientView> recipients) {}

}

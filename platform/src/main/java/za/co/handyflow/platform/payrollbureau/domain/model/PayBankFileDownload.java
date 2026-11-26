package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Who downloaded a bank file, and when. */
@Entity
@Table(name = "pay_bank_file_downloads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayBankFileDownload {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bank_file_id", nullable = false)
    private UUID bankFileId;

    @Column(name = "downloaded_by")
    private UUID downloadedBy;

    @Column(name = "downloaded_at", nullable = false)
    private Instant downloadedAt = Instant.now();

    public static PayBankFileDownload of(UUID tenantId, UUID bankFileId, UUID by) {
        PayBankFileDownload d = new PayBankFileDownload();
        d.tenantId = tenantId;
        d.bankFileId = bankFileId;
        d.downloadedBy = by;
        return d;
    }
}
